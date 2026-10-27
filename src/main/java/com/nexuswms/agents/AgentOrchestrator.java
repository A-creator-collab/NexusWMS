package com.nexuswms.agents;

import com.nexuswms.forecasting.ForecastService;
import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import com.nexuswms.service.*;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runs enabled agents (scheduled + on demand) and persists de-duplicated proposals as PENDING approval requests.
 * <p>Transaction design: (1) a short READ-ONLY transaction snapshots the database into an {@link AgentContext};
 * (2) agents run with NO transaction open, so a slow LLM call never holds a database connection or locks;
 * (3) a short WRITE transaction persists the proposals and the audit row. A lock prevents overlapping runs.
 */
@Service
public class AgentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final List<OrderStatus> OPEN = List.of(OrderStatus.DRAFT, OrderStatus.SUBMITTED, OrderStatus.CONFIRMED, OrderStatus.RECEIVING);

    private final List<Agent> agents;
    private final ForecastService forecasts;
    private final InventoryRepository inventory;
    private final PurchaseOrderRepository orders;
    private final ApprovalRequestRepository approvals;
    private final SettingsService settings;
    private final AuditService audit;
    private final NotificationService notes;
    private final TransactionTemplate readTx, writeTx;
    private final ReentrantLock running = new ReentrantLock();

    public AgentOrchestrator(List<Agent> agents, ForecastService forecasts, InventoryRepository inventory, PurchaseOrderRepository orders,
                             ApprovalRequestRepository approvals, SettingsService settings, AuditService audit, NotificationService notes,
                             PlatformTransactionManager tm) {
        this.agents = agents; this.forecasts = forecasts; this.inventory = inventory; this.orders = orders;
        this.approvals = approvals; this.settings = settings; this.audit = audit; this.notes = notes;
        this.readTx = new TransactionTemplate(tm); this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(tm);
    }

    /** @return number of NEW approval requests created. */
    public int runAll() {
        if (!running.tryLock()) throw new BusinessException("The agents are already running. Try again in a moment.");
        try {
            AgentContext ctx = readTx.execute(s -> new AgentContext(forecasts.recommendations(), inventory.findAllActive(),
                    orders.findByStatusIn(OPEN), forecasts, settings, LocalDate.now()));

            List<ProposedAction> all = new ArrayList<>();
            List<String> report = new ArrayList<>();
            boolean degraded = false;
            for (Agent a : agents) {
                if (!settings.agentEnabled(a.settingsKey())) { report.add(a.name() + ": disabled"); continue; }
                try {
                    List<ProposedAction> p = a.propose(ctx);
                    all.addAll(p);
                    String dg = a.diagnostics();
                    report.add(a.name() + ": " + p.size() + (dg == null || dg.isBlank() ? "" : " [" + dg + "]"));
                    degraded |= a.degraded();
                } catch (RuntimeException e) {
                    log.error("Agent {} failed", a.name(), e);
                    report.add(a.name() + ": FAILED (" + e.getClass().getSimpleName() + ")");
                }
            }
            final boolean warn = degraded;
            Integer created = writeTx.execute(s -> persist(all, report, warn));
            return created == null ? 0 : created;
        } finally {
            running.unlock();
        }
    }

    private int persist(List<ProposedAction> proposals, List<String> report, boolean degraded) {
        Set<String> seen = new HashSet<>();
        int created = 0;
        for (ProposedAction p : proposals) {
            String key = p.dedupKey();
            if (!seen.add(key) || approvals.existsByDedupKeyAndStatus(key, ApprovalStatus.PENDING)) continue;
            approvals.save(toRequest(p, key));
            created++;
        }
        audit.log("AGENT", null, "RUN", null, "created=" + created + " | " + String.join(" | ", report));
        final int n = created;
        AfterCommit.run(() -> {
            if (n > 0) notes.publish("INFO", n + " new approval request(s) pending.");
            if (degraded) notes.publish("WARN", "AI was unavailable for some agents; rule-based proposals were used. See Settings / Audit.");
        });
        return created;
    }

    static ApprovalRequest toRequest(ProposedAction p, String key) {
        ApprovalRequest r = new ApprovalRequest();
        r.agent = p.agent(); r.action = p.action(); r.productId = p.productId(); r.supplierId = p.supplierId(); r.orderId = p.orderId();
        r.quantity = p.quantity(); r.estimatedCost = p.estimatedCost(); r.newStatus = p.newStatus(); r.rationale = p.rationale();
        r.dedupKey = key; r.status = ApprovalStatus.PENDING;
        return r;
    }

    @Scheduled(initialDelayString = "${nexus.agents.initial-delay-ms:60000}", fixedDelayString = "${nexus.agents.interval-ms:3600000}")
    public void scheduled() {
        try { runAll(); }
        catch (BusinessException e) { log.info("Scheduled agent run skipped: {}", e.getMessage()); }
        catch (RuntimeException e) { log.error("Scheduled agent run failed", e); }
    }
}

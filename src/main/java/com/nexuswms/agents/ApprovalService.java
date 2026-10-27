package com.nexuswms.agents;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import com.nexuswms.security.Roles;
import com.nexuswms.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Human decision point. Approving executes the proposal through the normal services, in one transaction. */
@Service
public class ApprovalService {
    public record ApprovalRow(Long id, String agent, AgentAction action, String target, int quantity, BigDecimal cost,
                              String rationale, ApprovalStatus status, LocalDateTime createdAt, String decisionNote) {}

    private final ApprovalRequestRepository approvals;
    private final OrderService orders;
    private final ProductRepository products;
    private final PurchaseOrderRepository orderRepo;
    private final SupplierRepository suppliers;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationService notes;

    public ApprovalService(ApprovalRequestRepository approvals, OrderService orders, ProductRepository products, PurchaseOrderRepository orderRepo,
                           SupplierRepository suppliers, UserRepository users, AuditService audit, NotificationService notes) {
        this.approvals = approvals; this.orders = orders; this.products = products; this.orderRepo = orderRepo;
        this.suppliers = suppliers; this.users = users; this.audit = audit; this.notes = notes;
    }

    @Transactional(readOnly = true)
    public long pendingCount() { return approvals.countByStatus(ApprovalStatus.PENDING); }

    @Transactional(readOnly = true)
    public List<ApprovalRow> list(boolean pendingOnly) {
        List<ApprovalRequest> rs = pendingOnly ? approvals.findByStatusOrderByCreatedAtDescIdDesc(ApprovalStatus.PENDING)
                : approvals.findAllByOrderByCreatedAtDescIdDesc();
        return rs.stream().map(this::row).toList();
    }

    private ApprovalRow row(ApprovalRequest r) {
        String target = "";
        if (r.productId != null) target = products.findById(r.productId).map(p -> p.sku + " – " + p.name).orElse("product #" + r.productId);
        if (r.orderId != null) target = orderRepo.findById(r.orderId).map(o -> o.orderNumber + " → " + r.newStatus).orElse("order #" + r.orderId);
        return new ApprovalRow(r.id, r.agent, r.action, target, r.quantity, r.estimatedCost, r.rationale, r.status, r.createdAt, r.decisionNote);
    }

    @Transactional
    public void approve(Long id, Long actor, String note) {
        ApprovalRequest r = pending(id, actor);
        switch (r.action) {
            case CREATE_PO -> orders.submit(orders.createDraft(r.supplierId,
                    List.of(new OrderService.Line(r.productId, r.quantity, null)), null, actor).id, actor);
            case EXPEDITE_ORDER -> {
                int lead = suppliers.findById(r.supplierId).map(s -> s.leadTimeDays).orElse(4);
                orders.submit(orders.createDraft(r.supplierId, List.of(new OrderService.Line(r.productId, r.quantity, null)),
                        LocalDate.now().plusDays(Math.max(1, lead / 2)), actor).id, actor);
            }
            case ORDER_STATUS_CHANGE -> {
                if ("SUBMITTED".equals(r.newStatus)) orders.submit(r.orderId, actor);
                else if ("CANCELLED".equals(r.newStatus)) orders.cancel(r.orderId, actor);
                else throw new BusinessException("Unsupported status change " + r.newStatus);
            }
        }
        decide(r, ApprovalStatus.APPROVED, actor, note);
    }

    @Transactional public void reject(Long id, Long actor, String note) { decide(pending(id, actor), ApprovalStatus.REJECTED, actor, note); }
    @Transactional public void requestChanges(Long id, Long actor, String note) {
        if (note == null || note.isBlank()) throw new BusinessException("Please describe the changes you want.");
        decide(pending(id, actor), ApprovalStatus.CHANGES_REQUESTED, actor, note);
    }

    private ApprovalRequest pending(Long id, Long actor) {
        if (id == null || actor == null) throw new BusinessException("No request selected.");
        Role role = users.findById(actor).map(u -> u.role).orElse(null);
        if (role == null || !Roles.canApprove(role)) throw new BusinessException("Your role is not allowed to decide approvals.");
        ApprovalRequest r = approvals.findForUpdate(id).orElseThrow(() -> new BusinessException("Approval request not found."));
        if (r.status != ApprovalStatus.PENDING) throw new BusinessException("This request was already decided (" + r.status + ").");
        return r;
    }

    private void decide(ApprovalRequest r, ApprovalStatus s, Long actor, String note) {
        r.status = s; r.decidedBy = actor; r.decisionNote = note;
        approvals.save(r);
        audit.log("APPROVAL", r.id, s.name(), actor, r.agent + " " + r.action + " qty=" + r.quantity + (note == null ? "" : " note=" + note));
    }
}

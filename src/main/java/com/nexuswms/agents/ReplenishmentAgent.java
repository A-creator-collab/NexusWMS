package com.nexuswms.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.nexuswms.ai.*;
import com.nexuswms.forecasting.ReorderRecommendation;
import com.nexuswms.model.Inventory;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

/**
 * Replenishment planner.
 * <ol>
 *   <li>Reads the database state through the forecast layer (low stock, on-order, safety stock, supplier lead time/reliability).</li>
 *   <li>Sends a compact JSON snapshot to the LLM and asks for a strict JSON answer.</li>
 *   <li>Validates every item (known SKU, sane quantity, non-empty rationale); invalid items are dropped, out-of-range quantities are clamped.</li>
 *   <li>Returns {@link ProposedAction}s which the orchestrator persists as PENDING {@code ApprovalRequest}s. Nothing executes without a human.</li>
 * </ol>
 * If AI is off, misconfigured, unreachable or returns unusable JSON (after one repair attempt) the deterministic
 * forecast rule is used instead, and the rationale says so.
 */
@Component @Order(2)
public class ReplenishmentAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(ReplenishmentAgent.class);
    static final int MAX_CANDIDATES = 40;
    static final String SYSTEM = """
            You are the replenishment planner for a warehouse management system.
            You receive JSON describing products that may need reordering. Decide how many units to order for each product.
            Rules:
            - Use ONLY the SKUs provided. Never invent products.
            - quantity is an integer between 1 and 3 x recommendedQty. recommendedQty is the statistical baseline (forecast demand over lead time + safety stock - on hand - on order).
            - Order slightly more for suppliers with low reliability or long lead times; order less if stock is nearly sufficient or demand is tiny.
            - If an order is not worthwhile, omit it from "proposals" and list it in "skipped" with a reason.
            - rationale: one or two sentences (max 300 characters) citing numbers from the input.
            Respond with ONLY a JSON object, no markdown, in exactly this shape:
            {"proposals":[{"sku":"SKU-1","quantity":10,"rationale":"..."}],"skipped":[{"sku":"SKU-2","reason":"..."}]}
            """;

    private final LlmClient llm;
    private final ObjectMapper mapper;
    private volatile String diagnostics = "";
    private volatile boolean degraded;

    public ReplenishmentAgent(LlmClient llm, ObjectMapper mapper) { this.llm = llm; this.mapper = mapper; }

    @Override public String name() { return "ReplenishmentAgent"; }
    @Override public String settingsKey() { return "replenishment"; }
    @Override public String diagnostics() { return diagnostics; }
    @Override public boolean degraded() { return degraded; }

    @Override
    public List<ProposedAction> propose(AgentContext ctx) {
        degraded = false;
        List<ReorderRecommendation> candidates = ctx.recommendations().stream()
                .filter(r -> r.reorderQty() > 0 && r.supplierId() != null).limit(MAX_CANDIDATES).toList();
        if (candidates.isEmpty()) { diagnostics = "nothing to reorder"; return List.of(); }

        if (!llm.available()) {
            diagnostics = "rule-based (" + llm.status() + ")";
            return deterministic(candidates, "");
        }
        try {
            List<ProposedAction> out = viaLlm(ctx, candidates);
            diagnostics = "AI (" + out.size() + " proposal(s) from " + candidates.size() + " candidate(s))";
            return out;
        } catch (LlmException e) {
            degraded = e.kind() != LlmException.Kind.NOT_CONFIGURED;
            diagnostics = "fallback to rules: " + e.kind() + " – " + e.getMessage();
            log.warn("Replenishment AI failed, using rules: {}", diagnostics);
            return deterministic(candidates, "[rule-based: AI unavailable] ");
        }
    }

    // ---- LLM path ----------------------------------------------------------

    private List<ProposedAction> viaLlm(AgentContext ctx, List<ReorderRecommendation> candidates) throws LlmException {
        Map<Long, Inventory> byProduct = new HashMap<>();
        for (Inventory i : ctx.inventories()) byProduct.put(i.product.id, i);
        String prompt = buildPrompt(ctx, candidates, byProduct);

        JsonNode root;
        try {
            root = LlmJson.parseObject(mapper, llm.complete(SYSTEM, prompt));
        } catch (LlmException bad) {
            if (bad.kind() != LlmException.Kind.BAD_RESPONSE) throw bad;
            log.warn("AI returned unusable JSON ({}); asking once more", bad.getMessage());
            root = LlmJson.parseObject(mapper, llm.complete(SYSTEM, prompt
                    + "\n\nYour previous reply could not be parsed (" + bad.getMessage() + "). Reply again with ONLY the JSON object, nothing else."));
        }
        return map(root, candidates);
    }

    String buildPrompt(AgentContext ctx, List<ReorderRecommendation> candidates, Map<Long, Inventory> byProduct) {
        ObjectNode root = mapper.createObjectNode();
        root.put("today", ctx.today().toString());
        root.put("approvalThresholdUsd", ctx.settings().poThreshold());
        ArrayNode arr = root.putArray("products");
        for (ReorderRecommendation r : candidates) {
            Inventory inv = byProduct.get(r.productId());
            ObjectNode o = arr.addObject();
            o.put("sku", r.sku()); o.put("name", r.name());
            o.put("onHand", r.onHand()); o.put("onOrder", r.onOrder());
            if (inv != null) { o.put("reserved", inv.quantityReserved); o.put("reorderPoint", inv.product.reorderPoint);
                if (inv.product.supplier != null) { o.put("supplier", inv.product.supplier.name); o.put("leadTimeDays", inv.product.supplier.leadTimeDays);
                    o.put("supplierReliability", inv.product.supplier.reliabilityScore.doubleValue()); } }
            o.put("forecastDemandOverLeadTime", r.forecastDemand());
            o.put("safetyStock", r.safetyStock());
            o.put("recommendedQty", r.reorderQty());
            o.put("unitPriceUsd", r.unitPrice().doubleValue());
        }
        return root.toPrettyString();
    }

    /** Validates and converts the AI answer. Never trusts the model: SKUs, quantities and costs are checked/recomputed here. */
    List<ProposedAction> map(JsonNode root, List<ReorderRecommendation> candidates) throws LlmException {
        JsonNode props = root.get("proposals");
        if (props == null || !props.isArray()) throw new LlmException(LlmException.Kind.BAD_RESPONSE, "Missing \"proposals\" array");
        Map<String, ReorderRecommendation> bySku = new HashMap<>();
        for (ReorderRecommendation r : candidates) bySku.put(r.sku().toUpperCase(Locale.ROOT), r);
        Set<String> seen = new HashSet<>();
        List<ProposedAction> out = new ArrayList<>();
        for (JsonNode p : props) {
            String sku = text(p.get("sku")).toUpperCase(Locale.ROOT);
            ReorderRecommendation r = bySku.get(sku);
            if (r == null) { log.warn("AI proposed unknown SKU '{}' - dropped", sku); continue; }
            if (seen.contains(sku)) continue;
            Integer q = intValue(p.get("quantity"));
            if (q == null || q <= 0) { log.warn("AI proposed invalid quantity for {} - dropped", sku); continue; }
            seen.add(sku);
            int max = Math.max(1, Math.min(r.reorderQty() * 3, 1_000_000));
            String note = "";
            if (q > max) { note = " (AI asked for " + q + "; capped at " + max + ")"; q = max; }
            String why = text(p.get("rationale"));
            if (why.isBlank()) why = "Baseline reorder of " + r.reorderQty() + " units.";
            if (why.length() > 600) why = why.substring(0, 600);
            out.add(new ProposedAction(name(), AgentAction.CREATE_PO, r.productId(), r.supplierId(), null, q,
                    r.unitPrice().multiply(BigDecimal.valueOf(q)), null, "AI: " + why + note + " [baseline " + r.reorderQty() + "]"));
        }
        return out;
    }

    // ---- deterministic path ---------------------------------------------------

    private List<ProposedAction> deterministic(List<ReorderRecommendation> candidates, String prefix) {
        List<ProposedAction> out = new ArrayList<>();
        for (ReorderRecommendation r : candidates) {
            String why = prefix + String.format("Forecast demand over lead time %.1f + safety stock %d - on hand %d - on order %d = reorder %d of %s.",
                    r.forecastDemand(), r.safetyStock(), r.onHand(), r.onOrder(), r.reorderQty(), r.sku());
            out.add(new ProposedAction(name(), AgentAction.CREATE_PO, r.productId(), r.supplierId(), null, r.reorderQty(),
                    r.unitPrice().multiply(BigDecimal.valueOf(r.reorderQty())), null, why));
        }
        return out;
    }

    private static String text(JsonNode n) { return n == null || n.isNull() ? "" : n.asText("").trim(); }

    private static Integer intValue(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) { double d = n.asDouble(); return d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE ? (int) d : null; }
        try { return Integer.parseInt(n.asText().trim()); } catch (NumberFormatException e) { return null; }
    }
}

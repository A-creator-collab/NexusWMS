package com.nexuswms.agents;

import java.math.BigDecimal;

/** A consequential action an agent PROPOSES. Nothing executes until a human approves it. */
public record ProposedAction(String agent, AgentAction action, Long productId, Long supplierId, Long orderId,
                             int quantity, BigDecimal estimatedCost, String newStatus, String rationale) {
    /** Two proposals with the same key target the same thing; only the first is kept. */
    public String dedupKey() {
        return action == AgentAction.ORDER_STATUS_CHANGE ? "ORD:" + orderId + ":" + newStatus : "PO:" + productId;
    }
}

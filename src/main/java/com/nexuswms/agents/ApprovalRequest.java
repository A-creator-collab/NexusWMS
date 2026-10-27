package com.nexuswms.agents;

import com.nexuswms.model.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "approval_requests")
public class ApprovalRequest extends BaseEntity {
    @Column(nullable = false, length = 48) public String agent;
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "enum") public AgentAction action;
    @Column(name = "product_id") public Long productId;
    @Column(name = "supplier_id") public Long supplierId;
    @Column(name = "order_id") public Long orderId;
    @Column(nullable = false) public int quantity;
    @Column(name = "estimated_cost", nullable = false, precision = 12, scale = 2) public BigDecimal estimatedCost = BigDecimal.ZERO;
    @Column(name = "new_status", length = 16) public String newStatus;
    @Column(columnDefinition = "text") public String rationale;
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "enum") public ApprovalStatus status = ApprovalStatus.PENDING;
    @Column(name = "dedup_key", nullable = false, length = 128) public String dedupKey;
    @Column(name = "decided_by") public Long decidedBy;
    @Column(name = "decision_note", columnDefinition = "text") public String decisionNote;
}

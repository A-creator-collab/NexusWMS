package com.nexuswms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Append-only ledger row. Never updated after insert. */
@Entity @Table(name = "stock_movements")
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "product_id", nullable = false, updatable = false) public Product product;
    @Column(nullable = false, updatable = false) public int delta;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, columnDefinition = "enum") public MovementReason reason;
    @Column(name = "ref_order_id", updatable = false) public Long refOrderId;
    @Column(name = "actor_user_id", updatable = false) public Long actorUserId;
    @Column(columnDefinition = "text", updatable = false) public String note;
    @Column(name = "created_at", nullable = false, updatable = false) public LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

package com.nexuswms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "order_items")
public class OrderItem extends BaseEntity {
    @ManyToOne(optional = false) @JoinColumn(name = "order_id", nullable = false) public PurchaseOrder order;
    @ManyToOne(optional = false) @JoinColumn(name = "product_id", nullable = false) public Product product;
    @Column(name = "quantity_ordered", nullable = false) public int quantityOrdered;
    @Column(name = "quantity_received", nullable = false) public int quantityReceived;
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2) public BigDecimal unitCost = BigDecimal.ZERO;
    public int remaining() { return quantityOrdered - quantityReceived; }
}

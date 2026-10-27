package com.nexuswms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "orders")
public class PurchaseOrder extends BaseEntity {
    @Column(name = "order_number", nullable = false, unique = true, length = 32) public String orderNumber;
    @ManyToOne(optional = false) @JoinColumn(name = "supplier_id", nullable = false) public Supplier supplier;
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "enum") public OrderStatus status = OrderStatus.DRAFT;
    @Column(name = "expected_date") public LocalDate expectedDate;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2) public BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "created_by") public Long createdBy;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public List<OrderItem> items = new ArrayList<>();
}

package com.nexuswms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "products")
public class Product extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64) public String sku;
    @Column(nullable = false, length = 160) public String name;
    @Column(length = 64) public String category;
    @Column(columnDefinition = "text") public String description;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) public BigDecimal unitPrice = BigDecimal.ZERO;
    @Column(name = "reorder_point", nullable = false) public int reorderPoint;
    @Column(name = "safety_stock", nullable = false) public int safetyStock;
    @ManyToOne @JoinColumn(name = "supplier_id") public Supplier supplier;
    @Column(nullable = false) public boolean active = true;
    @Override public String toString() { return sku + " – " + name; }
}

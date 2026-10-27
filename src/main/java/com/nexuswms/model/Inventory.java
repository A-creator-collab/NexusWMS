package com.nexuswms.model;

import jakarta.persistence.*;

@Entity @Table(name = "inventory")
public class Inventory extends BaseEntity {
    @OneToOne @JoinColumn(name = "product_id", nullable = false, unique = true) public Product product;
    @Column(name = "quantity_on_hand", nullable = false) public int quantityOnHand;
    @Column(name = "quantity_reserved", nullable = false) public int quantityReserved;
    @Column(name = "bin_location", length = 32) public String binLocation;
    public int available() { return quantityOnHand - quantityReserved; }
}

package com.nexuswms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "suppliers")
public class Supplier extends BaseEntity {
    @Column(nullable = false, unique = true, length = 128) public String name;
    @Column(name = "contact_email") public String contactEmail;
    @Column(length = 32) public String phone;
    @Column(name = "lead_time_days", nullable = false) public int leadTimeDays = 7;
    @Column(name = "reliability_score", nullable = false, precision = 4, scale = 2) public BigDecimal reliabilityScore = new BigDecimal("0.80");
    @Column(nullable = false) public boolean active = true;
    @Override public String toString() { return name; }
}

package com.nexuswms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "forecasts")
public class Forecast {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "product_id", nullable = false) public Product product;
    @Column(name = "forecast_date", nullable = false) public LocalDate forecastDate;
    @Column(name = "predicted_demand", nullable = false, precision = 12, scale = 2) public BigDecimal predictedDemand;
    @Column(nullable = false, length = 32) public String method;
    @Column(precision = 4, scale = 2) public BigDecimal confidence;
    @Column(name = "created_at", nullable = false, updatable = false) public LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

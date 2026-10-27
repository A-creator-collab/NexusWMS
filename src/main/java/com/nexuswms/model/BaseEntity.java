package com.nexuswms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Surrogate key + created/updated timestamps (set in Java so values are visible immediately after save). */
@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist void onCreate() { LocalDateTime n = LocalDateTime.now(); if (createdAt == null) createdAt = n; updatedAt = n; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}

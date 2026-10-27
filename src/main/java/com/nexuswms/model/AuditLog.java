package com.nexuswms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "entity_type", nullable = false, length = 64) public String entityType;
    @Column(name = "entity_id") public Long entityId;
    @Column(nullable = false, length = 32) public String action;
    @Column(name = "actor_user_id") public Long actorUserId;
    @Column(columnDefinition = "text") public String details;
    @Column(name = "created_at", nullable = false, updatable = false) public LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

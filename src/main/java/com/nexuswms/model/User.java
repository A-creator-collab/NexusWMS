package com.nexuswms.model;

import jakarta.persistence.*;

@Entity @Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64) public String username;
    @Column(name = "password_hash", nullable = false, length = 100) public String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "enum") public Role role;
    @Column(nullable = false) public boolean active = true;
}

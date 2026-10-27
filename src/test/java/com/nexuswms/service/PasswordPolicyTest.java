package com.nexuswms.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    @Test void acceptsStrongPassword() { assertDoesNotThrow(() -> PasswordPolicy.check("alice", "Correct-horse-42")); }
    @Test void rejectsWeakOnes() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.check("alice", null));
        assertThrows(BusinessException.class, () -> PasswordPolicy.check("alice", "short1A"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.check("alice", "onlylettersonly"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.check("alice", "1234567890123"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.check("alice", "alice-password-1"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.check("alice", "a1".repeat(40)));
    }
    @Test void seededDefaultsAreDetected() {
        assertTrue(PasswordPolicy.isKnownDefault("admin123")); assertTrue(PasswordPolicy.isKnownDefault("Operator123"));
        assertFalse(PasswordPolicy.isKnownDefault("Correct-horse-42")); assertFalse(PasswordPolicy.isKnownDefault(null));
    }
}

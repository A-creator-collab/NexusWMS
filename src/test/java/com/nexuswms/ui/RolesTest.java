package com.nexuswms.ui;

import com.nexuswms.model.Role;
import com.nexuswms.security.Roles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Headless permission-matrix test (no JavaFX toolkit needed). */
class RolesTest {
    @Test void matrix() {
        assertTrue(Roles.canMutate(Role.OPERATOR)); assertFalse(Roles.canApprove(Role.OPERATOR));
        assertFalse(Roles.canMutate(Role.VIEWER)); assertFalse(Roles.canApprove(Role.VIEWER));
        assertTrue(Roles.canApprove(Role.MANAGER)); assertFalse(Roles.canManageUsers(Role.MANAGER));
        assertTrue(Roles.canManageUsers(Role.ADMIN));
    }
}

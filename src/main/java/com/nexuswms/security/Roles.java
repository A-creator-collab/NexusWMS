package com.nexuswms.security;

import com.nexuswms.model.Role;

/** Role permission matrix. ADMIN: all; MANAGER: all but user management; OPERATOR: create orders/adjust stock; VIEWER: read-only. */
public final class Roles {
    private Roles() {}
    public static boolean canMutate(Role r) { return r == Role.ADMIN || r == Role.MANAGER || r == Role.OPERATOR; }
    public static boolean canApprove(Role r) { return r == Role.ADMIN || r == Role.MANAGER; }
    public static boolean canManageUsers(Role r) { return r == Role.ADMIN; }
    public static boolean canConfigure(Role r) { return canApprove(r); }
}

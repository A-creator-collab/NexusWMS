package com.nexuswms.security;

import com.nexuswms.model.Role;
import org.springframework.stereotype.Component;

/** Holds the signed-in desktop user (single-user-per-process). */
@Component
public class SessionContext {
    private volatile Long userId; private volatile String username; private volatile Role role;
    public void signIn(Long id, String name, Role r) { userId = id; username = name; role = r; }
    public void signOut() { userId = null; username = null; role = null; }
    public Long userId() { return userId; }
    public String username() { return username; }
    public Role role() { return role; }
    public boolean canMutate() { return role != null && Roles.canMutate(role); }
    public boolean canApprove() { return role != null && Roles.canApprove(role); }
}

package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.UserRepository;
import com.nexuswms.security.Roles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/** User administration (ADMIN only) and self-service password change. Passwords are BCrypt-hashed and never logged or audited. */
@Service
public class UserService {
    public record UserRow(Long id, String username, Role role, boolean active, LocalDateTime createdAt) {}
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,32}$");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit) { this.users = users; this.encoder = encoder; this.audit = audit; }

    @Transactional(readOnly = true)
    public List<UserRow> list() { return users.findAllByOrderByUsername().stream().map(UserService::row).toList(); }

    @Transactional
    public UserRow create(String username, String password, Role role, Long actor) {
        requireAdmin(actor);
        String name = username == null ? "" : username.trim();
        if (!USERNAME.matcher(name).matches()) throw new BusinessException("Username must be 3–32 characters: letters, digits, '.', '_' or '-'.");
        if (role == null) throw new BusinessException("Choose a role.");
        if (users.existsByUsernameIgnoreCase(name)) throw new BusinessException("The username '" + name + "' is already taken.");
        PasswordPolicy.check(name, password);
        User u = new User();
        u.username = name; u.role = role; u.active = true; u.passwordHash = encoder.encode(password);
        u = users.save(u);
        audit.log("USER", u.id, "CREATE", actor, "username=" + name + " role=" + role);
        return row(u);
    }

    @Transactional
    public void setRole(Long id, Role role, Long actor) {
        requireAdmin(actor);
        if (role == null) throw new BusinessException("Choose a role.");
        User u = load(id);
        if (u.role == role) return;
        if (u.role == Role.ADMIN && u.active) requireAnotherAdmin(u);
        Role before = u.role;
        u.role = role; users.save(u);
        audit.log("USER", id, "SET_ROLE", actor, "username=" + u.username + " " + before + " -> " + role);
    }

    @Transactional
    public void setActive(Long id, boolean active, Long actor) {
        requireAdmin(actor);
        User u = load(id);
        if (u.active == active) return;
        if (!active && id.equals(actor)) throw new BusinessException("You cannot deactivate your own account.");
        if (!active && u.role == Role.ADMIN) requireAnotherAdmin(u);
        u.active = active; users.save(u);
        audit.log("USER", id, active ? "ACTIVATE" : "DEACTIVATE", actor, "username=" + u.username);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword, Long actor) {
        requireAdmin(actor);
        User u = load(id);
        PasswordPolicy.check(u.username, newPassword);
        u.passwordHash = encoder.encode(newPassword); users.save(u);
        audit.log("USER", id, "RESET_PASSWORD", actor, "username=" + u.username);
    }

    @Transactional
    public void changeOwnPassword(Long userId, String current, String newPassword) {
        User u = load(userId);
        if (current == null || !encoder.matches(current, u.passwordHash)) throw new BusinessException("The current password is incorrect.");
        if (current.equals(newPassword)) throw new BusinessException("The new password must differ from the current one.");
        PasswordPolicy.check(u.username, newPassword);
        u.passwordHash = encoder.encode(newPassword); users.save(u);
        audit.log("USER", userId, "CHANGE_PASSWORD", userId, "username=" + u.username);
    }

    /** UI helpers: return null on success or the user-presentable error message (the transaction rolled back). */
    public String changeOwnPasswordSafe(Long userId, String current, String newPassword) {
        try { changeOwnPassword(userId, current, newPassword); return null; } catch (BusinessException e) { return e.getMessage(); }
    }
    public String resetPasswordSafe(Long id, String newPassword, Long actor) {
        try { resetPassword(id, newPassword, actor); return null; } catch (BusinessException e) { return e.getMessage(); }
    }

    // ---- helpers ----
    private User load(Long id) {
        if (id == null) throw new BusinessException("No user selected.");
        return users.findById(id).orElseThrow(() -> new BusinessException("User not found."));
    }

    private void requireAdmin(Long actor) {
        Role r = actor == null ? null : users.findById(actor).filter(x -> x.active).map(x -> x.role).orElse(null);
        if (r == null || !Roles.canManageUsers(r)) throw new BusinessException("Only administrators can manage users.");
    }

    private void requireAnotherAdmin(User u) {
        long others = users.findAll().stream().filter(x -> x.active && x.role == Role.ADMIN && !x.id.equals(u.id)).count();
        if (others == 0) throw new BusinessException("At least one active administrator is required.");
    }

    static UserRow row(User u) { return new UserRow(u.id, u.username, u.role, u.active, u.createdAt); }
}

package com.nexuswms.security;

import com.nexuswms.model.User;
import com.nexuswms.repository.UserRepository;
import com.nexuswms.service.AuditService;
import com.nexuswms.service.BusinessException;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sign-in with BCrypt verification and a simple brute-force lockout (5 failures -> 60 s per username). */
@Service
public class AuthService {
    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofSeconds(60);

    private static final class Attempts { int failures; Instant lockedUntil = Instant.MIN; }

    private final NexusUserDetailsService details;
    private final PasswordEncoder encoder;
    private final UserRepository users;
    private final SessionContext session;
    private final AuditService audit;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public AuthService(NexusUserDetailsService details, PasswordEncoder encoder, UserRepository users, SessionContext session, AuditService audit) {
        this.details = details; this.encoder = encoder; this.users = users; this.session = session; this.audit = audit;
    }

    public User login(String username, String password) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || password == null || password.isEmpty()) throw new BusinessException("Enter your username and password.");
        String key = name.toLowerCase();
        Attempts a = attempts.computeIfAbsent(key, k -> new Attempts());
        synchronized (a) {
            if (Instant.now().isBefore(a.lockedUntil)) {
                long secs = Math.max(1, Duration.between(Instant.now(), a.lockedUntil).toSeconds());
                throw new BusinessException("Too many failed attempts. Try again in " + secs + " seconds.");
            }
        }
        try {
            UserDetails d = details.loadUserByUsername(name);
            if (!d.isEnabled() || !encoder.matches(password, d.getPassword())) return fail(name, a);
            User u = users.findByUsername(d.getUsername()).orElse(null);
            if (u == null) return fail(name, a);
            synchronized (a) { a.failures = 0; a.lockedUntil = Instant.MIN; }
            session.signIn(u.id, u.username, u.role);
            audit.log("AUTH", u.id, "LOGIN", u.id, "user=" + u.username);
            return u;
        } catch (UsernameNotFoundException e) {
            return fail(name, a);
        }
    }

    private User fail(String name, Attempts a) {
        synchronized (a) {
            a.failures++;
            if (a.failures >= MAX_FAILURES) { a.lockedUntil = Instant.now().plus(LOCKOUT); a.failures = 0; }
        }
        try { audit.log("AUTH", null, "LOGIN_FAILED", null, "user=" + (name.length() > 64 ? name.substring(0, 64) : name)); }
        catch (RuntimeException ignored) { /* auditing a failed login must never mask the failure */ }
        throw new BusinessException("Invalid username or password.");
    }
}

package com.nexuswms.service;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Password rules applied on every create/reset/change, plus detection of the seeded demo passwords. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 10, MAX_BYTES = 72;       // 72 = BCrypt input limit
    private static final Set<String> KNOWN_DEFAULTS = Set.of("admin123", "manager123", "operator123", "password", "changeme", "nexuswms");
    private PasswordPolicy() {}

    public static boolean isKnownDefault(String pw) { return pw != null && KNOWN_DEFAULTS.contains(pw.toLowerCase()); }

    public static void check(String username, String pw) {
        if (pw == null || pw.isBlank()) throw new BusinessException("Enter a password.");
        if (pw.length() < MIN_LENGTH) throw new BusinessException("Password must be at least " + MIN_LENGTH + " characters.");
        if (pw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new BusinessException("Password is too long (max " + MAX_BYTES + " bytes).");
        if (pw.chars().noneMatch(Character::isLetter) || pw.chars().noneMatch(Character::isDigit))
            throw new BusinessException("Password must contain at least one letter and one digit.");
        if (isKnownDefault(pw)) throw new BusinessException("That password is a well-known default. Choose another.");
        if (username != null && !username.isBlank() && pw.toLowerCase().contains(username.toLowerCase()))
            throw new BusinessException("Password must not contain the username.");
    }
}

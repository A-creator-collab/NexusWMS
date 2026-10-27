package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    UserRepository users = mock(UserRepository.class);
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder(4);
    AuditService audit = mock(AuditService.class);
    UserService svc = new UserService(users, enc, audit);
    User admin = user(1L, "admin", Role.ADMIN, true), manager = user(2L, "manager", Role.MANAGER, true);

    User user(Long id, String name, Role r, boolean active) { User u = new User(); u.id = id; u.username = name; u.role = r; u.active = active; u.passwordHash = enc.encode("Old-pass-123"); return u; }

    @BeforeEach void setUp() {
        when(users.findById(1L)).thenReturn(Optional.of(admin)); when(users.findById(2L)).thenReturn(Optional.of(manager));
        when(users.findAll()).thenReturn(List.of(admin, manager));
        when(users.save(any())).thenAnswer(i -> { User u = i.getArgument(0); if (u.id == null) u.id = 9L; return u; });
    }

    @Test void onlyAdministratorsManageUsers() {
        assertThrows(BusinessException.class, () -> svc.create("newuser", "Str0ng-password", Role.VIEWER, 2L));
        assertThrows(BusinessException.class, () -> svc.setRole(2L, Role.ADMIN, 2L));
        assertThrows(BusinessException.class, () -> svc.create("newuser", "Str0ng-password", Role.VIEWER, null));
        verify(users, never()).save(any());
    }
    @Test void createHashesPasswordAndAudits() {
        UserService.UserRow row = svc.create("j.smith", "Str0ng-password", Role.OPERATOR, 1L);
        assertEquals("j.smith", row.username());
        verify(users).save(argThat(u -> u.passwordHash.startsWith("$2") && enc.matches("Str0ng-password", u.passwordHash)));
        verify(audit).log(eq("USER"), any(), eq("CREATE"), eq(1L), argThat(d -> !d.contains("Str0ng")));       // never audit secrets
    }
    @Test void usernameRulesAndDuplicates() {
        assertThrows(BusinessException.class, () -> svc.create("a b", "Str0ng-password", Role.VIEWER, 1L));
        assertThrows(BusinessException.class, () -> svc.create("ab", "Str0ng-password", Role.VIEWER, 1L));
        when(users.existsByUsernameIgnoreCase("manager")).thenReturn(true);
        assertThrows(BusinessException.class, () -> svc.create("manager", "Str0ng-password", Role.VIEWER, 1L));
    }
    @Test void lastAdministratorIsProtected() {
        assertThrows(BusinessException.class, () -> svc.setRole(1L, Role.MANAGER, 1L));
        assertThrows(BusinessException.class, () -> svc.setActive(1L, false, 1L));
        User second = user(3L, "boss", Role.ADMIN, true); when(users.findById(3L)).thenReturn(Optional.of(second)); when(users.findAll()).thenReturn(List.of(admin, manager, second));
        svc.setRole(3L, Role.MANAGER, 1L); assertEquals(Role.MANAGER, second.role);
    }
    @Test void cannotDeactivateYourself() {
        User second = user(3L, "boss", Role.ADMIN, true); when(users.findAll()).thenReturn(List.of(admin, manager, second));
        assertThrows(BusinessException.class, () -> svc.setActive(1L, false, 1L));
    }
    @Test void deactivateAndReactivate() {
        svc.setActive(2L, false, 1L); assertFalse(manager.active);
        svc.setActive(2L, true, 1L); assertTrue(manager.active);
    }
    @Test void changeOwnPasswordNeedsCorrectCurrentAndPolicy() {
        assertThrows(BusinessException.class, () -> svc.changeOwnPassword(2L, "wrong", "Another-pass-9"));
        assertThrows(BusinessException.class, () -> svc.changeOwnPassword(2L, "Old-pass-123", "Old-pass-123"));
        assertThrows(BusinessException.class, () -> svc.changeOwnPassword(2L, "Old-pass-123", "short1"));
        svc.changeOwnPassword(2L, "Old-pass-123", "Another-pass-9");
        assertTrue(enc.matches("Another-pass-9", manager.passwordHash));
        assertNull(svc.changeOwnPasswordSafe(2L, "Another-pass-9", "Third-pass-77"));
        assertNotNull(svc.changeOwnPasswordSafe(2L, "bad", "Third-pass-78"));
    }
    @Test void resetPasswordAppliesPolicyToTargetUser() {
        assertThrows(BusinessException.class, () -> svc.resetPassword(2L, "manager-password-1", 1L));       // contains username
        svc.resetPassword(2L, "Fresh-password-5", 1L); assertTrue(enc.matches("Fresh-password-5", manager.passwordHash));
    }
}

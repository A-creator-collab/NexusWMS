package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.UserRepository;
import com.nexuswms.security.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder(4);
    UserRepository users = mock(UserRepository.class);
    SessionContext session = new SessionContext();
    AuthService auth;

    @BeforeEach void setUp() {
        User u = new User(); u.id = 4L; u.username = "op"; u.passwordHash = enc.encode("secret"); u.role = Role.OPERATOR; u.active = true;
        when(users.findByUsername("op")).thenReturn(Optional.of(u));
        auth = new AuthService(new NexusUserDetailsService(users), enc, users, session, mock(AuditService.class));
    }
    @Test void goodPasswordSignsIn() { auth.login("op", "secret"); assertEquals("op", session.username()); assertTrue(session.canMutate()); assertFalse(session.canApprove()); }
    @Test void badPasswordRejected() { assertThrows(BusinessException.class, () -> auth.login("op", "nope")); assertNull(session.userId()); }
    @Test void unknownUserRejected() { assertThrows(BusinessException.class, () -> auth.login("ghost", "x")); }

    @Test void repeatedFailuresLockTheAccountEvenForTheCorrectPassword() {
        for (int i = 0; i < 5; i++) assertThrows(BusinessException.class, () -> auth.login("op", "wrong"));
        BusinessException e = assertThrows(BusinessException.class, () -> auth.login("op", "secret"));
        assertTrue(e.getMessage().contains("Too many failed attempts")); assertNull(session.userId());
    }
    @Test void blankCredentialsRejected() { assertThrows(BusinessException.class, () -> auth.login("", "")); assertThrows(BusinessException.class, () -> auth.login("op", null)); }
}

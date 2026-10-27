package com.nexuswms.service;

import com.nexuswms.model.*;
import com.nexuswms.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditAndNotificationTest {
    @Test void logPersistsAllFields() {
        AuditLogRepository repo = mock(AuditLogRepository.class);
        new AuditService(repo, mock(UserRepository.class)).log("PRODUCT", 3L, "CREATE", 9L, "d");
        ArgumentCaptor<AuditLog> c = ArgumentCaptor.forClass(AuditLog.class); verify(repo).save(c.capture());
        assertEquals("PRODUCT", c.getValue().entityType); assertEquals(3L, c.getValue().entityId);
        assertEquals("CREATE", c.getValue().action); assertEquals(9L, c.getValue().actorUserId); assertEquals("d", c.getValue().details);
    }
    @Test void searchFiltersByTypeAndActor() {
        AuditLogRepository repo = mock(AuditLogRepository.class); UserRepository users = mock(UserRepository.class);
        User u = new User(); u.id = 1L; u.username = "admin"; when(users.findAll()).thenReturn(List.of(u));
        when(repo.findByCreatedAtBetweenOrderByCreatedAtDescIdDesc(any(), any(), any())).thenReturn(List.of(log("ORDER", 1L), log("PRODUCT", 1L), log("PRODUCT", null)));
        AuditService svc = new AuditService(repo, users);
        assertEquals(3, svc.search(null, null, "ALL", "", 100).size());
        assertEquals(2, svc.search(null, null, "PRODUCT", "", 100).size());
        List<AuditService.AuditRow> admin = svc.search(null, null, "ALL", "adm", 100);
        assertEquals(2, admin.size()); assertEquals("admin", admin.get(0).actor());
    }
    private AuditLog log(String type, Long actor) { AuditLog a = new AuditLog(); a.entityType = type; a.action = "X"; a.actorUserId = actor; a.createdAt = LocalDateTime.now(); return a; }

    @Test void notificationsTrackUnreadAndNotifyListeners() {
        NotificationService ns = new NotificationService(); AtomicInteger seen = new AtomicInteger();
        ns.addListener(n -> seen.incrementAndGet());
        ns.publish("INFO", "a"); ns.publish("WARN", "b");
        assertEquals(2, ns.unread()); assertEquals(2, seen.get()); assertEquals("b", ns.recent().get(0).message());
        ns.markRead(); assertEquals(0, ns.unread());
    }
}

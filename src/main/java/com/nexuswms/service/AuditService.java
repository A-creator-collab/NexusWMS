package com.nexuswms.service;

import com.nexuswms.model.AuditLog;
import com.nexuswms.model.User;
import com.nexuswms.repository.AuditLogRepository;
import com.nexuswms.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Explicit audit trail. Every service mutation calls {@link #log} inside its own transaction. */
@Service
public class AuditService {
    public record AuditRow(LocalDateTime at, String entityType, Long entityId, String action, String actor, String details) {}
    private final AuditLogRepository repo;
    private final UserRepository users;

    public AuditService(AuditLogRepository repo, UserRepository users) { this.repo = repo; this.users = users; }

    @Transactional
    public void log(String entityType, Long entityId, String action, Long actorId, String details) {
        AuditLog a = new AuditLog();
        a.entityType = entityType; a.entityId = entityId; a.action = action; a.actorUserId = actorId; a.details = details;
        repo.save(a);
    }

    @Transactional(readOnly = true)
    public List<AuditRow> search(LocalDate from, LocalDate to, String entityType, String actorContains, int limit) {
        LocalDateTime f = (from == null ? LocalDate.of(2000, 1, 1) : from).atStartOfDay();
        LocalDateTime t = (to == null ? LocalDate.of(2999, 1, 1) : to.plusDays(1)).atStartOfDay();
        Map<Long, String> names = new HashMap<>();
        for (User u : users.findAll()) names.put(u.id, u.username);
        String et = entityType == null || entityType.isBlank() || entityType.equals("ALL") ? null : entityType;
        String ac = actorContains == null ? "" : actorContains.trim().toLowerCase();
        List<AuditRow> out = new ArrayList<>();
        for (AuditLog a : repo.findByCreatedAtBetweenOrderByCreatedAtDescIdDesc(f, t, PageRequest.of(0, 2000))) {
            if (et != null && !et.equals(a.entityType)) continue;
            String actor = a.actorUserId == null ? "system" : names.getOrDefault(a.actorUserId, "#" + a.actorUserId);
            if (!ac.isEmpty() && !actor.toLowerCase().contains(ac)) continue;
            out.add(new AuditRow(a.createdAt, a.entityType, a.entityId, a.action, actor, a.details));
            if (out.size() >= limit) break;
        }
        return out;
    }
}

package com.nexuswms.repository;

import com.nexuswms.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDescIdDesc(LocalDateTime from, LocalDateTime to, Pageable page);
}

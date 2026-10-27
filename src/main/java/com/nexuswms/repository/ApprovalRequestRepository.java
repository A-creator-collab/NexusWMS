package com.nexuswms.repository;

import com.nexuswms.agents.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    List<ApprovalRequest> findByStatusOrderByCreatedAtDescIdDesc(ApprovalStatus status);
    List<ApprovalRequest> findAllByOrderByCreatedAtDescIdDesc();
    boolean existsByDedupKeyAndStatus(String dedupKey, ApprovalStatus status);
    long countByStatus(ApprovalStatus status);

    /** Prevents a double-click / two managers from approving (and executing) the same proposal twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ApprovalRequest a where a.id = :id")
    Optional<ApprovalRequest> findForUpdate(@Param("id") Long id);
}

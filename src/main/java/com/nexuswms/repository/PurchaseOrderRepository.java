package com.nexuswms.repository;

import com.nexuswms.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findAllByOrderByCreatedAtDescIdDesc();
    List<PurchaseOrder> findByStatusOrderByCreatedAtDescIdDesc(OrderStatus status);
    List<PurchaseOrder> findBySupplierIdOrderByCreatedAtDescIdDesc(Long supplierId);
    List<PurchaseOrder> findByStatusIn(Collection<OrderStatus> statuses);
    long countByStatusIn(Collection<OrderStatus> statuses);
    boolean existsByOrderNumber(String orderNumber);
    boolean existsBySupplierId(Long supplierId);
    boolean existsBySupplierIdAndStatusIn(Long supplierId, Collection<OrderStatus> statuses);

    /** Row lock so two users cannot receive/cancel/transition the same order concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findForUpdate(@Param("id") Long id);
}

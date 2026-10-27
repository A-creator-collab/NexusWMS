package com.nexuswms.repository;

import com.nexuswms.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @Query(value = "select i from Inventory i join fetch i.product p left join fetch p.supplier s where p.active = true "
            + "and (lower(p.name) like :q or lower(p.sku) like :q or lower(coalesce(p.category,'')) like :q) order by p.sku",
           countQuery = "select count(i) from Inventory i join i.product p where p.active = true "
            + "and (lower(p.name) like :q or lower(p.sku) like :q or lower(coalesce(p.category,'')) like :q)")
    Page<Inventory> search(@Param("q") String q, Pageable pageable);

    @Query("select i from Inventory i join fetch i.product p left join fetch p.supplier where p.active = true order by p.sku")
    List<Inventory> findAllActive();

    @Query("select i from Inventory i join fetch i.product p left join fetch p.supplier where p.active = true "
         + "and (i.quantityOnHand - i.quantityReserved) <= p.reorderPoint order by (i.quantityOnHand - i.quantityReserved)")
    List<Inventory> findLowStock();

    Optional<Inventory> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i join fetch i.product where i.product.id = :pid")
    Optional<Inventory> findForUpdate(@Param("pid") Long productId);
}

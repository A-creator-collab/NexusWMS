package com.nexuswms.repository;

import com.nexuswms.model.StockMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable page);
    List<StockMovement> findAllByOrderByCreatedAtDescIdDesc(Pageable page);
    List<StockMovement> findByProductIdAndCreatedAtAfter(Long productId, LocalDateTime after);
    List<StockMovement> findByCreatedAtAfter(LocalDateTime after);
    boolean existsByProductId(Long productId);
}

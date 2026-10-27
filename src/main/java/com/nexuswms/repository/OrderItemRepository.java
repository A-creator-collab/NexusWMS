package com.nexuswms.repository;

import com.nexuswms.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByProductId(Long productId);
    boolean existsByProductIdAndOrderStatusIn(Long productId, Collection<OrderStatus> statuses);
    List<OrderItem> findByProductIdAndOrderStatusIn(Long productId, Collection<OrderStatus> statuses);
}

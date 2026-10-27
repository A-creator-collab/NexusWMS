package com.nexuswms.repository;

import com.nexuswms.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySkuIgnoreCase(String sku);
    Optional<Product> findBySkuIgnoreCase(String sku);
    List<Product> findByActiveTrueOrderBySku();
    long countByActiveTrue();
}

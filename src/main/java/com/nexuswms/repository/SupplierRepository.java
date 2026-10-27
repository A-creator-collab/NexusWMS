package com.nexuswms.repository;

import com.nexuswms.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByActiveTrueOrderByName();
    List<Supplier> findAllByOrderByName();
    boolean existsByNameIgnoreCase(String name);
    java.util.Optional<Supplier> findByNameIgnoreCase(String name);
}

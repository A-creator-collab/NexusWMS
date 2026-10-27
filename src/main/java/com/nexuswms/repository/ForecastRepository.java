package com.nexuswms.repository;

import com.nexuswms.model.Forecast;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ForecastRepository extends JpaRepository<Forecast, Long> {
    List<Forecast> findByProductIdOrderByForecastDateDescIdDesc(Long productId);
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}

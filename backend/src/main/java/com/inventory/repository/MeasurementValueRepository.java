package com.inventory.repository;

import com.inventory.entity.MeasurementValue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementValueRepository extends JpaRepository<MeasurementValue, Long> {

    boolean existsByPointId(Long pointId);
}

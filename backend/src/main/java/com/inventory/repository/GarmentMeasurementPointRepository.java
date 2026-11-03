package com.inventory.repository;

import com.inventory.entity.GarmentMeasurementPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GarmentMeasurementPointRepository extends JpaRepository<GarmentMeasurementPoint, Long> {

    List<GarmentMeasurementPoint> findByGarmentTypeIdOrderBySortOrderAscIdAsc(Long garmentTypeId);
}

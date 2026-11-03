package com.inventory.repository;

import com.inventory.entity.MeasurementRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MeasurementRecordRepository extends JpaRepository<MeasurementRecord, Long> {

    @EntityGraph(attributePaths = {"customer", "garmentType"})
    @Query(value = """
            select m from MeasurementRecord m
            where (:customerId is null or m.customer.id = :customerId)
              and (:garmentTypeId is null or m.garmentType.id = :garmentTypeId)
              and (:search = '' or lower(m.customer.name) like concat('%', :search, '%'))
            """,
            countQuery = """
            select count(m) from MeasurementRecord m
            where (:customerId is null or m.customer.id = :customerId)
              and (:garmentTypeId is null or m.garmentType.id = :garmentTypeId)
              and (:search = '' or lower(m.customer.name) like concat('%', :search, '%'))
            """)
    Page<MeasurementRecord> search(@Param("customerId") Long customerId, @Param("garmentTypeId") Long garmentTypeId,
                                   @Param("search") String search, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "garmentType"})
    Optional<MeasurementRecord> findWithDetailsById(Long id);

    boolean existsByCustomerId(Long customerId);

    boolean existsByGarmentTypeId(Long garmentTypeId);
}

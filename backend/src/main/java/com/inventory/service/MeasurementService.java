package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.measurement.MeasurementDtos.MeasurementRequest;
import com.inventory.dto.measurement.MeasurementDtos.MeasurementResponse;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.entity.AuditableEntity;
import com.inventory.entity.Customer;
import com.inventory.entity.GarmentMeasurementPoint;
import com.inventory.entity.GarmentType;
import com.inventory.entity.MeasurementRecord;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.CustomerRepository;
import com.inventory.repository.GarmentMeasurementPointRepository;
import com.inventory.repository.GarmentTypeRepository;
import com.inventory.repository.MeasurementRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.inventory.service.MasterDataSupport.money;
import static com.inventory.service.MasterDataSupport.searchTerm;
import static com.inventory.service.MasterDataSupport.trimToNull;

/**
 * Measurement records: taken, read, never edited or deleted. A changed measurement is a new record.
 * Only active points may be measured on a new record; deactivated points still show on old records.
 */
@Service
public class MeasurementService {

    private final MeasurementRecordRepository records;
    private final CustomerRepository customers;
    private final GarmentTypeRepository types;
    private final GarmentMeasurementPointRepository points;
    private final UserDirectory directory;
    private final Clock clock;
    private final ZoneId zone;

    public MeasurementService(MeasurementRecordRepository records, CustomerRepository customers,
                              GarmentTypeRepository types, GarmentMeasurementPointRepository points,
                              UserDirectory directory, Clock clock, ZoneId businessZone) {
        this.records = records;
        this.customers = customers;
        this.types = types;
        this.points = points;
        this.directory = directory;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Transactional(readOnly = true)
    public PageResponse<MeasurementResponse> search(Long customerId, Long garmentTypeId, String search, Pageable pageable) {
        Page<MeasurementRecord> page = records.search(customerId, garmentTypeId, searchTerm(search), pageable);
        Map<Long, Ref> names = directory.refs(page.getContent().stream().map(AuditableEntity::getCreatedBy).toList());
        return PageResponse.of(page, m -> toResponse(m, names));
    }

    @Transactional(readOnly = true)
    public MeasurementResponse get(Long id) {
        MeasurementRecord record = records.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Measurement", id));
        return toResponse(record, Map.of());
    }

    @Transactional
    public MeasurementResponse create(Long customerId, MeasurementRequest request) {
        Customer customer = customers.findByIdAndWalkInFalse(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        GarmentType type = types.findById(request.garmentTypeId())
                .orElseThrow(() -> new BusinessException("Selected garment type does not exist"));
        if (!type.isActive()) {
            throw new BusinessException("Garment type '" + type.getName() + "' is inactive");
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate takenOn = request.takenOn() == null ? today : request.takenOn();
        if (takenOn.isAfter(today)) {
            throw new BusinessException("The measurement date cannot be in the future");
        }

        Map<Long, GarmentMeasurementPoint> pointsById = new HashMap<>();
        for (GarmentMeasurementPoint p : points.findByGarmentTypeIdOrderBySortOrderAscIdAsc(type.getId())) {
            pointsById.put(p.getId(), p);
        }

        MeasurementRecord record = new MeasurementRecord(customer, type, takenOn, trimToNull(request.notes()));
        Set<Long> entered = new HashSet<>();
        for (var value : request.values()) {
            GarmentMeasurementPoint point = pointsById.get(value.pointId());
            if (point == null) {
                throw new BusinessException("A measurement point does not belong to '" + type.getName() + "'");
            }
            if (!point.isActive()) {
                throw new BusinessException("Measurement point '" + point.getLabel()
                        + "' is no longer used for new measurements");
            }
            if (!entered.add(point.getId())) {
                throw new BusinessException("Measurement point '" + point.getLabel() + "' is entered twice");
            }
            record.addValue(point, money(value.value()));
        }
        MeasurementRecord saved = records.saveAndFlush(record);
        return toResponse(saved, Map.of());
    }

    private MeasurementResponse toResponse(MeasurementRecord m, Map<Long, Ref> names) {
        Long creatorId = m.getCreatedBy();
        Ref creator = creatorId == null ? null
                : names.containsKey(creatorId) ? names.get(creatorId) : directory.ref(creatorId);
        return MasterDataMapper.toResponse(m, creator == null ? null : creator.name());
    }
}

package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.garment.GarmentDtos.GarmentTypeRequest;
import com.inventory.dto.garment.GarmentDtos.GarmentTypeResponse;
import com.inventory.dto.garment.GarmentDtos.PointRequest;
import com.inventory.entity.GarmentMeasurementPoint;
import com.inventory.entity.GarmentType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.GarmentMeasurementPointRepository;
import com.inventory.repository.GarmentTypeRepository;
import com.inventory.repository.MeasurementRecordRepository;
import com.inventory.repository.MeasurementValueRepository;
import com.inventory.repository.OrderLineRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.deleteIfUnused;
import static com.inventory.service.MasterDataSupport.inUse;
import static com.inventory.service.MasterDataSupport.searchTerm;
import static com.inventory.service.MasterDataSupport.trimToNull;

/**
 * Garment types and the measurement points each one takes (in inches).
 *
 * <p>Points are never silently lost: a point removed from a type is deleted only if no measurement
 * ever used it; otherwise it is deactivated, so old records keep their labels and new forms hide it.
 */
@Service
public class GarmentTypeService {

    private final GarmentTypeRepository types;
    private final GarmentMeasurementPointRepository points;
    private final MeasurementRecordRepository records;
    private final MeasurementValueRepository values;
    private final OrderLineRepository orderLines;

    public GarmentTypeService(GarmentTypeRepository types, GarmentMeasurementPointRepository points,
                              MeasurementRecordRepository records, MeasurementValueRepository values,
                              OrderLineRepository orderLines) {
        this.types = types;
        this.points = points;
        this.records = records;
        this.values = values;
        this.orderLines = orderLines;
    }

    @Transactional(readOnly = true)
    public PageResponse<GarmentTypeResponse> search(String search, Boolean active, Pageable pageable) {
        Page<GarmentType> page = types.search(searchTerm(search), active, pageable);
        return PageResponse.of(page, this::respond);
    }

    @Transactional(readOnly = true)
    public GarmentTypeResponse get(Long id) {
        return respond(load(id));
    }

    @Transactional
    public GarmentTypeResponse create(GarmentTypeRequest request) {
        String name = request.name().trim();
        if (types.existsByNameIgnoreCase(name)) {
            throw duplicateName(name);
        }
        List<PointRequest> incoming = request.points() == null ? List.of() : request.points();
        if (incoming.stream().anyMatch(p -> p.id() != null)) {
            throw new BusinessException("Unknown measurement point: save the garment type first");
        }
        checkLabels(incoming);

        GarmentType type = new GarmentType(name, request.shape());
        type.setDescription(trimToNull(request.description()));
        type.setActive(request.active() == null || request.active());
        types.save(type);
        for (PointRequest p : incoming) {
            GarmentMeasurementPoint point = new GarmentMeasurementPoint(type);
            point.setLabel(p.label().trim());
            point.setSortOrder(p.sortOrder());
            point.setActive(p.active() == null || p.active());
            points.save(point);
        }
        return respond(type);
    }

    @Transactional
    public GarmentTypeResponse update(Long id, GarmentTypeRequest request) {
        GarmentType type = load(id);
        checkVersion(type, request.version());
        String name = request.name().trim();
        if (types.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName(name);
        }
        List<PointRequest> incoming = request.points() == null ? List.of() : request.points();
        checkLabels(incoming);

        Map<Long, GarmentMeasurementPoint> existing = new HashMap<>();
        for (GarmentMeasurementPoint p : points.findByGarmentTypeIdOrderBySortOrderAscIdAsc(id)) {
            existing.put(p.getId(), p);
        }
        Set<Long> kept = new HashSet<>();
        for (PointRequest p : incoming) {
            if (p.id() != null) {
                if (!existing.containsKey(p.id())) {
                    throw new BusinessException("Unknown measurement point for '" + type.getName() + "'");
                }
                kept.add(p.id());
            }
        }

        // A removed point keeps its label while it still exists, so no new label may take it.
        Set<String> newLabels = new HashSet<>();
        incoming.forEach(p -> newLabels.add(p.label().trim().toLowerCase(Locale.ROOT)));
        List<GarmentMeasurementPoint> removed = new ArrayList<>();
        for (GarmentMeasurementPoint p : existing.values()) {
            if (kept.contains(p.getId())) continue;
            if (newLabels.contains(p.getLabel().toLowerCase(Locale.ROOT))) {
                throw new BusinessException("Label '" + p.getLabel() + "' belongs to a point that is being removed. "
                        + "Choose another label");
            }
            removed.add(p);
        }

        List<GarmentMeasurementPoint> toDelete = new ArrayList<>();
        for (GarmentMeasurementPoint p : removed) {
            if (values.existsByPointId(p.getId())) {
                p.setActive(false);
            } else {
                toDelete.add(p);
            }
        }
        if (!toDelete.isEmpty()) {
            points.deleteAll(toDelete);
            points.flush();
        }

        // Labels are swapped through temporary values so that two points can trade labels safely.
        for (PointRequest p : incoming) {
            if (p.id() != null) {
                existing.get(p.id()).setLabel("~" + p.id());
            }
        }
        points.flush();

        for (PointRequest p : incoming) {
            GarmentMeasurementPoint point = p.id() == null
                    ? new GarmentMeasurementPoint(type)
                    : existing.get(p.id());
            point.setLabel(p.label().trim());
            point.setSortOrder(p.sortOrder());
            point.setActive(p.active() == null || p.active());
            if (p.id() == null) {
                points.save(point);
            }
        }

        type.setName(name);
        type.setDescription(trimToNull(request.description()));
        type.setShape(request.shape());
        type.setActive(request.active() == null || request.active());
        types.saveAndFlush(type);
        points.flush();
        return respond(type);
    }

    /** Only a garment type never used by a measurement or an order can be deleted. */
    @Transactional
    public void delete(Long id) {
        GarmentType type = load(id);
        String label = "Garment type '" + type.getName() + "'";
        if (inUseByRecords(id)) {
            throw inUse(label);
        }
        points.deleteAll(points.findByGarmentTypeIdOrderBySortOrderAscIdAsc(id));
        points.flush();
        deleteIfUnused(types, type, label);
    }

    private GarmentTypeResponse respond(GarmentType type) {
        List<GarmentMeasurementPoint> list = points.findByGarmentTypeIdOrderBySortOrderAscIdAsc(type.getId());
        return MasterDataMapper.toResponse(type, list, inUseByRecords(type.getId()));
    }

    private boolean inUseByRecords(Long typeId) {
        return records.existsByGarmentTypeId(typeId) || orderLines.existsByGarmentTypeId(typeId);
    }

    /** Labels must be present, unique (ignoring case) and have a sort order. */
    private static void checkLabels(List<PointRequest> incoming) {
        Set<String> seen = new HashSet<>();
        for (PointRequest p : incoming) {
            if (p.sortOrder() == null) {
                throw new BusinessException("Sort order is required for point '" + p.label().trim() + "'");
            }
            if (!seen.add(p.label().trim().toLowerCase(Locale.ROOT))) {
                throw new BusinessException("Measurement point '" + p.label().trim() + "' is listed twice");
            }
        }
    }

    private GarmentType load(Long id) {
        return types.findById(id).orElseThrow(() -> new ResourceNotFoundException("Garment type", id));
    }

    private static BusinessException duplicateName(String name) {
        return new BusinessException(HttpStatus.CONFLICT, "A garment type named '" + name + "' already exists");
    }
}

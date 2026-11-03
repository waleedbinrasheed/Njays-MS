package com.inventory.mapper;

import com.inventory.dto.fabric.FabricDtos.FabricResponse;
import com.inventory.dto.garment.GarmentDtos.GarmentRef;
import com.inventory.dto.garment.GarmentDtos.GarmentTypeResponse;
import com.inventory.dto.garment.GarmentDtos.PointResponse;
import com.inventory.dto.master.MasterDataDtos.CustomerResponse;
import com.inventory.dto.measurement.MeasurementDtos.MeasurementResponse;
import com.inventory.dto.measurement.MeasurementDtos.MeasurementValueResponse;
import com.inventory.entity.Customer;
import com.inventory.entity.GarmentMeasurementPoint;
import com.inventory.entity.GarmentType;
import com.inventory.entity.Inventory;
import com.inventory.entity.MeasurementRecord;
import com.inventory.entity.MeasurementValue;
import com.inventory.entity.Product;

import java.util.List;

public final class MasterDataMapper {

    private MasterDataMapper() {
    }

    public static CustomerResponse toResponse(Customer c, long orderCount) {
        return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getAddress(),
                c.isActive(), c.getVersion(), orderCount);
    }

    public static FabricResponse toResponse(Inventory i) {
        Product p = i.getProduct();
        return new FabricResponse(
                p.getId(), p.getFabricCode(), p.getTitle(), p.getMaterial(), p.getColour(), p.getPattern(),
                p.getWidth(), p.getPurchasePrice(), p.getSellingPrice(), p.isActive(), p.getVersion(),
                i.getQuantity(), i.getReorderLevel(), i.isLowStock());
    }

    public static GarmentRef garmentRef(GarmentType g) {
        return new GarmentRef(g.getId(), g.getName(), g.getShape());
    }

    public static PointResponse toResponse(GarmentMeasurementPoint p) {
        return new PointResponse(p.getId(), p.getLabel(), p.getSortOrder(), p.isActive());
    }

    public static GarmentTypeResponse toResponse(GarmentType g, List<GarmentMeasurementPoint> points, boolean inUse) {
        return new GarmentTypeResponse(g.getId(), g.getName(), g.getDescription(), g.getShape(), g.isActive(),
                g.getVersion(), points.stream().map(MasterDataMapper::toResponse).toList(), inUse);
    }

    public static MeasurementResponse toResponse(MeasurementRecord m, String createdBy) {
        List<MeasurementValueResponse> values = m.getValues().stream().map(MasterDataMapper::toResponse).toList();
        return new MeasurementResponse(m.getId(), m.getCustomer().getId(), m.getCustomer().getName(),
                garmentRef(m.getGarmentType()), m.getTakenOn(), m.getNotes(), m.getCreatedAt(), createdBy, values);
    }

    private static MeasurementValueResponse toResponse(MeasurementValue v) {
        return new MeasurementValueResponse(v.getPoint().getId(), v.getPointLabel(), v.getValue(), v.getSortOrder());
    }
}

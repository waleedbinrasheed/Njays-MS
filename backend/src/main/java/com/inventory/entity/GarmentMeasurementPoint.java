package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One measurement taken for a garment type, in inches. Inactive points are hidden from new forms only. */
@Entity
@Table(name = "garment_measurement_point")
public class GarmentMeasurementPoint extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garment_type_id", nullable = false, updatable = false)
    private GarmentType garmentType;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected GarmentMeasurementPoint() {
    }

    public GarmentMeasurementPoint(GarmentType garmentType) {
        this.garmentType = garmentType;
    }

    public GarmentType getGarmentType() {
        return garmentType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/** One inch value of a measurement record. The label and order are snapshots taken when it was recorded. */
@Entity
@Immutable
@Table(name = "measurement_value")
public class MeasurementValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private MeasurementRecord record;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_id", nullable = false)
    private GarmentMeasurementPoint point;

    @Column(name = "point_label", nullable = false, length = 80)
    private String pointLabel;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal value;

    protected MeasurementValue() {
    }

    MeasurementValue(MeasurementRecord record, GarmentMeasurementPoint point, BigDecimal value) {
        this.record = record;
        this.point = point;
        this.pointLabel = point.getLabel();
        this.sortOrder = point.getSortOrder();
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public GarmentMeasurementPoint getPoint() {
        return point;
    }

    public String getPointLabel() {
        return pointLabel;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public BigDecimal getValue() {
        return value;
    }
}

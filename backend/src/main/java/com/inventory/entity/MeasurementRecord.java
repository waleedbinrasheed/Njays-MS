package com.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Measurements of one customer for one garment type on one day. Records are never edited or deleted:
 * a changed measurement is recorded as a new record. Only the constructor and {@link #addValue} write data.
 */
@Entity
@Table(name = "measurement_record")
public class MeasurementRecord extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garment_type_id", nullable = false, updatable = false)
    private GarmentType garmentType;

    @Column(name = "taken_on", nullable = false, updatable = false)
    private LocalDate takenOn;

    @Column(length = 500, updatable = false)
    private String notes;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<MeasurementValue> values = new ArrayList<>();

    protected MeasurementRecord() {
    }

    public MeasurementRecord(Customer customer, GarmentType garmentType, LocalDate takenOn, String notes) {
        this.customer = customer;
        this.garmentType = garmentType;
        this.takenOn = takenOn;
        this.notes = notes;
    }

    public void addValue(GarmentMeasurementPoint point, BigDecimal value) {
        values.add(new MeasurementValue(this, point, value));
    }

    public Customer getCustomer() {
        return customer;
    }

    public GarmentType getGarmentType() {
        return garmentType;
    }

    public LocalDate getTakenOn() {
        return takenOn;
    }

    public String getNotes() {
        return notes;
    }

    public List<MeasurementValue> getValues() {
        return values;
    }
}

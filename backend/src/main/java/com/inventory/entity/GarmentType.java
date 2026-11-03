package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A dress type (Shalwar Kameez, Waistcoat, ...). {@code code} identifies the five built-in types, whose
 * reference pictures the frontend shows; user-added types have no code and no picture.
 */
@Entity
@Table(name = "garment_type")
public class GarmentType extends AuditableEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 30)
    private String code;

    @Column(length = 255)
    private String description;

    /** Stitching charge copied into a new order line when this dress is chosen; the order can change it. */
    @Column(name = "default_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    protected GarmentType() {
    }

    public GarmentType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getDefaultPrice() {
        return defaultPrice;
    }

    public void setDefaultPrice(BigDecimal defaultPrice) {
        this.defaultPrice = defaultPrice;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

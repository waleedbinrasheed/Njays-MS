package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "stock_adjustment")
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdjustmentReason reason;

    @Column(name = "previous_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal previousQuantity;

    @Column(name = "adjustment_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal adjustmentQuantity;

    @Column(name = "resulting_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal resultingQuantity;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockAdjustment() {
    }

    public StockAdjustment(Product product, AdjustmentReason reason, BigDecimal previousQuantity,
                           BigDecimal adjustmentQuantity, String notes, AppUser createdBy, Instant createdAt) {
        this.product = product;
        this.reason = reason;
        this.previousQuantity = previousQuantity;
        this.adjustmentQuantity = adjustmentQuantity;
        this.resultingQuantity = previousQuantity.add(adjustmentQuantity);
        this.notes = notes;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public AdjustmentReason getReason() {
        return reason;
    }

    public BigDecimal getPreviousQuantity() {
        return previousQuantity;
    }

    public BigDecimal getAdjustmentQuantity() {
        return adjustmentQuantity;
    }

    public BigDecimal getResultingQuantity() {
        return resultingQuantity;
    }

    public String getNotes() {
        return notes;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

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

    @Column(name = "previous_quantity", nullable = false)
    private int previousQuantity;

    @Column(name = "adjustment_quantity", nullable = false)
    private int adjustmentQuantity;

    @Column(name = "resulting_quantity", nullable = false)
    private int resultingQuantity;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockAdjustment() {
    }

    public StockAdjustment(Product product, AdjustmentReason reason, int previousQuantity, int adjustmentQuantity,
                           String notes, AppUser createdBy, Instant createdAt) {
        this.product = product;
        this.reason = reason;
        this.previousQuantity = previousQuantity;
        this.adjustmentQuantity = adjustmentQuantity;
        this.resultingQuantity = previousQuantity + adjustmentQuantity;
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

    public int getPreviousQuantity() {
        return previousQuantity;
    }

    public int getAdjustmentQuantity() {
        return adjustmentQuantity;
    }

    public int getResultingQuantity() {
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

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

/** Write-once ledger row. There are no setters: history is never edited. */
@Entity
@Immutable
@Table(name = "stock_transaction")
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType type;

    /** Signed change: positive adds stock, negative removes it. */
    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 20)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "previous_quantity", nullable = false)
    private int previousQuantity;

    @Column(name = "resulting_quantity", nullable = false)
    private int resultingQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(length = 500)
    private String notes;

    protected StockTransaction() {
    }

    public StockTransaction(Product product, TransactionType type, int quantity, int previousQuantity,
                            ReferenceType referenceType, Long referenceId, AppUser createdBy, Instant createdAt,
                            String notes) {
        this.product = product;
        this.type = type;
        this.quantity = quantity;
        this.previousQuantity = previousQuantity;
        this.resultingQuantity = previousQuantity + quantity;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public TransactionType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public int getPreviousQuantity() {
        return previousQuantity;
    }

    public int getResultingQuantity() {
        return resultingQuantity;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getNotes() {
        return notes;
    }
}

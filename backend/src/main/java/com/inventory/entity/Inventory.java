package com.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Current stock of one product, in metres for fabrics (3 decimal places). Only {@code InventoryService}
 * may change {@link #quantity}; it always records a {@link StockTransaction} in the same transaction.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false, unique = true)
    private Product product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reorder_level", nullable = false, precision = 12, scale = 3)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Inventory() {
    }

    public Inventory(Product product, Instant now) {
        this.product = product;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getReorderLevel() {
        return reorderLevel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /** Low stock: quantity at or below the reorder level (out of stock included). */
    public boolean isLowStock() {
        return quantity.compareTo(reorderLevel) <= 0;
    }

    public void changeReorderLevel(BigDecimal reorderLevel, Instant now) {
        this.reorderLevel = reorderLevel;
        this.updatedAt = now;
    }

    /** Called only by InventoryService after validation, in the same transaction as the ledger insert. */
    public void applyQuantityChange(BigDecimal change, Instant now) {
        this.quantity = this.quantity.add(change);
        this.updatedAt = now;
    }
}

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

import java.time.Instant;

/**
 * Current stock of one product. Only {@code InventoryService} may change {@link #quantity}; it always
 * records a {@link StockTransaction} in the same database transaction.
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

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

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

    public int getQuantity() {
        return quantity;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean isLowStock() {
        return quantity <= reorderLevel;
    }

    public void changeReorderLevel(int reorderLevel, Instant now) {
        this.reorderLevel = reorderLevel;
        this.updatedAt = now;
    }

    /** Called only by InventoryService after validation, in the same transaction as the ledger insert. */
    public void applyQuantityChange(int change, Instant now) {
        this.quantity += change;
        this.updatedAt = now;
    }
}

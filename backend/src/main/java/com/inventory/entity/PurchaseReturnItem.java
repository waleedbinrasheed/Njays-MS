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

@Entity
@Immutable
@Table(name = "purchase_return_item")
public class PurchaseReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_return_id", nullable = false)
    private PurchaseReturn purchaseReturn;

    /** The original document line being returned. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_item_id", nullable = false)
    private PurchaseItem purchaseItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected PurchaseReturnItem() {
    }

    public PurchaseReturnItem(PurchaseItem purchaseItem, int quantity, BigDecimal lineTotal) {
        this.purchaseItem = purchaseItem;
        this.product = purchaseItem.getProduct();
        this.quantity = quantity;
        this.lineTotal = lineTotal;
    }

    void attachTo(PurchaseReturn purchaseReturn) {
        this.purchaseReturn = purchaseReturn;
    }

    public Long getId() {
        return id;
    }

    public PurchaseItem getPurchaseItem() {
        return purchaseItem;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}

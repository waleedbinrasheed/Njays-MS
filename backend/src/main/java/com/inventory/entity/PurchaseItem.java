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
@Table(name = "purchase_item")
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercent;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected PurchaseItem() {
    }

    public PurchaseItem(Product product, int quantity, BigDecimal unitCost, BigDecimal discountPercent,
                        BigDecimal discountAmount, BigDecimal taxPercent, BigDecimal taxAmount, BigDecimal lineTotal) {
        this.product = product;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.discountPercent = discountPercent;
        this.discountAmount = discountAmount;
        this.taxPercent = taxPercent;
        this.taxAmount = taxAmount;
        this.lineTotal = lineTotal;
    }

    void attachTo(Purchase purchase, int lineNo) {
        this.purchase = purchase;
        this.lineNo = lineNo;
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxPercent() {
        return taxPercent;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}

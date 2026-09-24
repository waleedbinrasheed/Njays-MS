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

/** Goods sent back to the supplier from a purchase (stock out, credit due). Immutable once saved. */
@Entity
@Table(name = "purchase_return")
public class PurchaseReturn extends AuditableEntity {

    @Column(name = "return_number", nullable = false, updatable = false, length = 20)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @Column(name = "return_date", nullable = false, updatable = false)
    private LocalDate returnDate;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(length = 500, updatable = false)
    private String notes;

    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseReturnItem> items = new ArrayList<>();

    protected PurchaseReturn() {
    }

    public PurchaseReturn(String returnNumber, Purchase purchase, LocalDate returnDate, String reason, String notes) {
        this.returnNumber = returnNumber;
        this.purchase = purchase;
        this.returnDate = returnDate;
        this.reason = reason;
        this.notes = notes;
    }

    public void addItem(PurchaseReturnItem item) {
        item.attachTo(this);
        items.add(item);
        total = total.add(item.getLineTotal());
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getReason() {
        return reason;
    }

    public String getNotes() {
        return notes;
    }

    public List<PurchaseReturnItem> getItems() {
        return items;
    }
}

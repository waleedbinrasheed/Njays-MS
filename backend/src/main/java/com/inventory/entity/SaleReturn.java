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

/** Goods a customer brought back from a sales invoice (stock in, refund due). Immutable once saved. */
@Entity
@Table(name = "sale_return")
public class SaleReturn extends AuditableEntity {

    @Column(name = "return_number", nullable = false, updatable = false, length = 20)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @Column(name = "return_date", nullable = false, updatable = false)
    private LocalDate returnDate;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(length = 500, updatable = false)
    private String notes;

    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SaleReturnItem> items = new ArrayList<>();

    protected SaleReturn() {
    }

    public SaleReturn(String returnNumber, Sale sale, LocalDate returnDate, String reason, String notes) {
        this.returnNumber = returnNumber;
        this.sale = sale;
        this.returnDate = returnDate;
        this.reason = reason;
        this.notes = notes;
    }

    public void addItem(SaleReturnItem item) {
        item.attachTo(this);
        items.add(item);
        total = total.add(item.getLineTotal());
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public Sale getSale() {
        return sale;
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

    public List<SaleReturnItem> getItems() {
        return items;
    }
}

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

/** Samples a representative brought back (stock in) against one sample issue. Immutable once saved. */
@Entity
@Table(name = "sample_return")
public class SampleReturn extends AuditableEntity {

    @Column(name = "return_number", nullable = false, updatable = false, length = 20)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_issue_id", nullable = false, updatable = false)
    private SampleIssue sampleIssue;

    @Column(name = "return_date", nullable = false, updatable = false)
    private LocalDate returnDate;

    @Column(name = "total_quantity", nullable = false, updatable = false)
    private int totalQuantity;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 500, updatable = false)
    private String notes;

    @OneToMany(mappedBy = "sampleReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SampleReturnItem> items = new ArrayList<>();

    protected SampleReturn() {
    }

    public SampleReturn(String returnNumber, SampleIssue sampleIssue, LocalDate returnDate, String notes) {
        this.returnNumber = returnNumber;
        this.sampleIssue = sampleIssue;
        this.returnDate = returnDate;
        this.notes = notes;
    }

    public void addItem(SampleReturnItem item) {
        item.attachTo(this);
        items.add(item);
        totalQuantity += item.getQuantity();
        total = total.add(item.getLineTotal());
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public SampleIssue getSampleIssue() {
        return sampleIssue;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getNotes() {
        return notes;
    }

    public List<SampleReturnItem> getItems() {
        return items;
    }
}

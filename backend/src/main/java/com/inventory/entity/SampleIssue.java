package com.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A "SAMPLE SALE": books given to a representative at (roughly) cost. Not a sale and never counted
 * as one. Lines and prices are fixed once saved; returns only add to the returned totals, which
 * also decide the status.
 */
@Entity
@Table(name = "sample_issue")
public class SampleIssue extends AuditableEntity {

    @Column(name = "sample_number", nullable = false, updatable = false, length = 20)
    private String sampleNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "representative_id", nullable = false, updatable = false)
    private Representative representative;

    @Column(name = "issue_date", nullable = false, updatable = false)
    private LocalDate issueDate;

    @Column(name = "total_quantity", nullable = false, updatable = false)
    private int totalQuantity;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "returned_quantity", nullable = false)
    private int returnedQuantity;

    @Column(name = "returned_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal returnedTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SampleStatus status = SampleStatus.ISSUED;

    @Column(length = 500, updatable = false)
    private String notes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private AppUser cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "sampleIssue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<SampleIssueItem> items = new ArrayList<>();

    protected SampleIssue() {
    }

    public SampleIssue(String sampleNumber, Representative representative, LocalDate issueDate, String notes) {
        this.sampleNumber = sampleNumber;
        this.representative = representative;
        this.issueDate = issueDate;
        this.notes = notes;
    }

    public void addItem(SampleIssueItem item) {
        item.attachTo(this, items.size() + 1);
        items.add(item);
        totalQuantity += item.getQuantity();
        total = total.add(item.getLineTotal());
    }

    /** Adds a return to the running totals and works out the new status. */
    public void recordReturn(int quantity, BigDecimal amount) {
        returnedQuantity += quantity;
        returnedTotal = returnedTotal.add(amount);
        status = returnedQuantity >= totalQuantity ? SampleStatus.RETURNED : SampleStatus.PARTIALLY_RETURNED;
    }

    public void cancel(AppUser by, Instant at, String reason) {
        this.status = SampleStatus.CANCELLED;
        this.cancelledBy = by;
        this.cancelledAt = at;
        this.cancelReason = reason;
    }

    public boolean isCancelled() {
        return status == SampleStatus.CANCELLED;
    }

    public String getSampleNumber() {
        return sampleNumber;
    }

    public Representative getRepresentative() {
        return representative;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public int getReturnedQuantity() {
        return returnedQuantity;
    }

    public BigDecimal getReturnedTotal() {
        return returnedTotal;
    }

    /** Cancelled samples have nothing outstanding (all stock went back). */
    public int getOutstandingQuantity() {
        return isCancelled() ? 0 : totalQuantity - returnedQuantity;
    }

    public BigDecimal getOutstandingTotal() {
        return isCancelled() ? BigDecimal.ZERO : total.subtract(returnedTotal);
    }

    public SampleStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public AppUser getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<SampleIssueItem> getItems() {
        return items;
    }
}

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
 * A sales invoice. The buyer is the {@link #representative}; {@link #customer} optionally records
 * which of the representative's customers it is for. (Invoices from before representatives existed
 * have only a customer.) Lines and amounts are fixed once saved; only the payment status can change
 * afterwards, and cancelling returns the sold stock.
 */
@Entity
@Table(name = "sale")
public class Sale extends AuditableEntity {

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 20)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "representative_id", updatable = false)
    private Representative representative;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", updatable = false)
    private Customer customer;

    @Column(name = "sale_date", nullable = false, updatable = false)
    private LocalDate saleDate;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "invoice_discount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal invoiceDiscount;

    @Column(name = "discount_total", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal discountTotal;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal taxTotal;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 10)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DocumentStatus status = DocumentStatus.COMPLETED;

    @Column(length = 500, updatable = false)
    private String notes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private AppUser cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<SaleItem> items = new ArrayList<>();

    protected Sale() {
    }

    public Sale(String invoiceNumber, Representative representative, Customer customer, LocalDate saleDate,
                PaymentStatus paymentStatus, String notes) {
        this.invoiceNumber = invoiceNumber;
        this.representative = representative;
        this.customer = customer;
        this.saleDate = saleDate;
        this.paymentStatus = paymentStatus;
        this.notes = notes;
    }

    public void addItem(SaleItem item) {
        item.attachTo(this, items.size() + 1);
        items.add(item);
    }

    public void setTotals(BigDecimal subtotal, BigDecimal invoiceDiscount, BigDecimal discountTotal,
                          BigDecimal taxTotal, BigDecimal total) {
        this.subtotal = subtotal;
        this.invoiceDiscount = invoiceDiscount;
        this.discountTotal = discountTotal;
        this.taxTotal = taxTotal;
        this.total = total;
    }

    public void cancel(AppUser by, Instant at, String reason) {
        this.status = DocumentStatus.CANCELLED;
        this.cancelledBy = by;
        this.cancelledAt = at;
        this.cancelReason = reason;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public boolean isCancelled() {
        return status == DocumentStatus.CANCELLED;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public Representative getRepresentative() {
        return representative;
    }

    /** May be null for new sales (the customer is optional). */
    public Customer getCustomer() {
        return customer;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getInvoiceDiscount() {
        return invoiceDiscount;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public DocumentStatus getStatus() {
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

    public List<SaleItem> getItems() {
        return items;
    }
}

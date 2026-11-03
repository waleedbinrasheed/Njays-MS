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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** An advance or instalment received for an order. Payments are never edited or deleted. */
@Entity
@Immutable
@Table(name = "order_payment")
public class OrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentKind kind;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMethod method;

    @Column(length = 255)
    private String note;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private AppUser recordedBy;

    protected OrderPayment() {
    }

    public OrderPayment(CustomerOrder order, PaymentKind kind, BigDecimal amount, LocalDate paidOn,
                        PaymentMethod method, String note, Instant recordedAt, AppUser recordedBy) {
        this.order = order;
        this.kind = kind;
        this.amount = amount;
        this.paidOn = paidOn;
        this.method = method;
        this.note = note;
        this.recordedAt = recordedAt;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public PaymentKind getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getNote() {
        return note;
    }

    public AppUser getRecordedBy() {
        return recordedBy;
    }
}

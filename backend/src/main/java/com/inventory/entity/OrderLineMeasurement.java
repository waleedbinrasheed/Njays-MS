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

/** A measurement copied onto an order line when the order is saved. It never follows later record changes. */
@Entity
@Immutable
@Table(name = "order_line_measurement")
public class OrderLineMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_line_id", nullable = false)
    private OrderLine line;

    @Column(name = "point_label", nullable = false, length = 80)
    private String pointLabel;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal value;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected OrderLineMeasurement() {
    }

    OrderLineMeasurement(OrderLine line, String pointLabel, BigDecimal value, int sortOrder) {
        this.line = line;
        this.pointLabel = pointLabel;
        this.value = value;
        this.sortOrder = sortOrder;
    }

    public String getPointLabel() {
        return pointLabel;
    }

    public BigDecimal getValue() {
        return value;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

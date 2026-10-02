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
@Table(name = "sample_return_item")
public class SampleReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_return_id", nullable = false)
    private SampleReturn sampleReturn;

    /** The issued line being returned. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_issue_item_id", nullable = false)
    private SampleIssueItem sampleIssueItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected SampleReturnItem() {
    }

    /** Valued at the original sample price of the issued line. */
    public SampleReturnItem(SampleIssueItem issued, int quantity) {
        this.sampleIssueItem = issued;
        this.product = issued.getProduct();
        this.quantity = quantity;
        this.unitPrice = issued.getUnitPrice();
        this.lineTotal = issued.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
    }

    void attachTo(SampleReturn sampleReturn) {
        this.sampleReturn = sampleReturn;
    }

    public Long getId() {
        return id;
    }

    public SampleIssueItem getSampleIssueItem() {
        return sampleIssueItem;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}

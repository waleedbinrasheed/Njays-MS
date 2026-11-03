package com.inventory.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One garment in an order. Fabric name and price are snapshots: a later change to the fabric never
 * changes a saved order. Measurements are copied from the chosen record when the order is saved.
 */
@Entity
@Table(name = "order_line")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garment_type_id", nullable = false)
    private GarmentType garmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "fabric_source", nullable = false, length = 10)
    private FabricSource fabricSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fabric_product_id")
    private Product fabricProduct;

    @Column(name = "fabric_name", length = 300)
    private String fabricName;

    @Column(name = "fabric_metres", precision = 12, scale = 3)
    private BigDecimal fabricMetres;

    @Column(name = "customer_fabric_note", length = 200)
    private String customerFabricNote;

    @Column(name = "fabric_price_per_metre", precision = 12, scale = 2)
    private BigDecimal fabricPricePerMetre;

    @Column(name = "fabric_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal fabricAmount = BigDecimal.ZERO;

    @Column(name = "stitching_charge", nullable = false, precision = 14, scale = 2)
    private BigDecimal stitchingCharge = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    /** Reference only: the values were copied into {@link #measurements}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_record_id")
    private MeasurementRecord measurementRecord;

    @Column(length = 255)
    private String notes;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<OrderLineMeasurement> measurements = new ArrayList<>();

    protected OrderLine() {
    }

    public OrderLine(CustomerOrder order, int lineNo, GarmentType garmentType, FabricSource fabricSource) {
        this.order = order;
        this.lineNo = lineNo;
        this.garmentType = garmentType;
        this.fabricSource = fabricSource;
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public int getLineNo() {
        return lineNo;
    }

    public GarmentType getGarmentType() {
        return garmentType;
    }

    public FabricSource getFabricSource() {
        return fabricSource;
    }

    public Product getFabricProduct() {
        return fabricProduct;
    }

    public void setFabricProduct(Product fabricProduct) {
        this.fabricProduct = fabricProduct;
    }

    public String getFabricName() {
        return fabricName;
    }

    public void setFabricName(String fabricName) {
        this.fabricName = fabricName;
    }

    public BigDecimal getFabricMetres() {
        return fabricMetres;
    }

    public void setFabricMetres(BigDecimal fabricMetres) {
        this.fabricMetres = fabricMetres;
    }

    public String getCustomerFabricNote() {
        return customerFabricNote;
    }

    public void setCustomerFabricNote(String customerFabricNote) {
        this.customerFabricNote = customerFabricNote;
    }

    public BigDecimal getFabricPricePerMetre() {
        return fabricPricePerMetre;
    }

    public void setFabricPricePerMetre(BigDecimal fabricPricePerMetre) {
        this.fabricPricePerMetre = fabricPricePerMetre;
    }

    public BigDecimal getFabricAmount() {
        return fabricAmount;
    }

    public void setFabricAmount(BigDecimal fabricAmount) {
        this.fabricAmount = fabricAmount;
    }

    public BigDecimal getStitchingCharge() {
        return stitchingCharge;
    }

    public void setStitchingCharge(BigDecimal stitchingCharge) {
        this.stitchingCharge = stitchingCharge;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public MeasurementRecord getMeasurementRecord() {
        return measurementRecord;
    }

    public void setMeasurementRecord(MeasurementRecord measurementRecord) {
        this.measurementRecord = measurementRecord;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<OrderLineMeasurement> getMeasurements() {
        return measurements;
    }

    public void addMeasurement(String pointLabel, BigDecimal value, int sortOrder) {
        measurements.add(new OrderLineMeasurement(this, pointLabel, value, sortOrder));
    }
}

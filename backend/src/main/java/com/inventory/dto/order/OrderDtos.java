package com.inventory.dto.order;

import com.inventory.dto.garment.GarmentDtos.GarmentRef;
import com.inventory.entity.FabricSource;
import com.inventory.entity.OrderStatus;
import com.inventory.entity.PaymentKind;
import com.inventory.entity.PaymentMethod;
import com.inventory.entity.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Money and metres are validated here, but totals are always computed by the server. Requests on
 * update carry {@code version}; payments and status history are append-only and carry no version.
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    public enum CalendarDateType {
        DUE,
        TRIAL,
        DELIVERY
    }

    /**
     * STOCK lines need {@code fabricProductId} and {@code fabricMetres}; CUSTOMER lines (cloth supplied by
     * the customer) must leave both empty and may carry {@code customerFabricNote}.
     */
    public record OrderLineRequest(
            @NotNull(message = "Garment type is required") Long garmentTypeId,
            @NotNull(message = "Choose where the fabric comes from") FabricSource fabricSource,
            Long fabricProductId,
            @Digits(integer = 9, fraction = 3, message = "Metres must have at most 3 decimals") BigDecimal fabricMetres,
            @Size(max = 200) String customerFabricNote,
            @DecimalMin(value = "0.00", message = "Stitching charge cannot be negative")
            @Digits(integer = 12, fraction = 2, message = "Stitching charge must have at most 2 decimals")
            BigDecimal stitchingCharge,
            Long measurementRecordId,
            @Size(max = 255) String notes) {
    }

    public record OrderRequest(
            @NotNull(message = "Customer is required") Long customerId,
            LocalDate orderDate,
            @NotNull(message = "Due date is required") LocalDate dueDate,
            LocalDate trialDate,
            LocalDate deliveryDate,
            @Size(max = 500) String notes,
            @Valid @NotNull(message = "Add at least one garment")
            @Size(min = 1, message = "Add at least one garment") List<OrderLineRequest> lines,
            Long version) {
    }

    public record StatusRequest(
            @NotNull(message = "Status is required") OrderStatus status,
            @Size(max = 255) String note,
            Long version) {
    }

    public record CancelRequest(
            @NotBlank(message = "Give a reason for cancelling") @Size(max = 255) String reason,
            Long version) {
    }

    public record PaymentRequest(
            @NotNull(message = "Payment kind is required") PaymentKind kind,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
            @Digits(integer = 12, fraction = 2, message = "Amount must have at most 2 decimals")
            BigDecimal amount,
            LocalDate paidOn,
            @NotNull(message = "Payment method is required") PaymentMethod method,
            @Size(max = 255) String note) {
    }

    public record CustomerRef(Long id, String name, String phone, String address) {
    }

    public record FabricRef(Long id, String code, String name, String colour) {
    }

    public record OrderLineMeasurementResponse(String label, BigDecimal value, int sortOrder) {
    }

    public record OrderLineResponse(
            Long id, int lineNo, GarmentRef garmentType, FabricSource fabricSource, FabricRef fabric,
            BigDecimal fabricMetres, String customerFabricNote, BigDecimal fabricPricePerMetre,
            BigDecimal fabricAmount, BigDecimal stitchingCharge, BigDecimal lineTotal, Long measurementRecordId,
            List<OrderLineMeasurementResponse> measurements, String notes) {
    }

    public record PaymentResponse(
            Long id, PaymentKind kind, BigDecimal amount, LocalDate paidOn, PaymentMethod method,
            String note, String recordedBy) {
    }

    public record StatusHistoryResponse(
            OrderStatus fromStatus, OrderStatus toStatus, Instant changedAt, String changedBy, String note) {
    }

    public record OrderSummary(
            Long id, String orderNumber, OrderStatus status, String customerName, LocalDate dueDate,
            BigDecimal total, BigDecimal balance, PaymentStatus paymentStatus, int lineCount) {
    }

    public record OrderResponse(
            Long id, String orderNumber, OrderStatus status, CustomerRef customer, LocalDate orderDate,
            LocalDate dueDate, LocalDate trialDate, LocalDate deliveryDate, String notes,
            BigDecimal total, BigDecimal paid, BigDecimal balance, PaymentStatus paymentStatus,
            String cancelReason, List<OrderLineResponse> lines, List<PaymentResponse> payments,
            List<StatusHistoryResponse> statusHistory, Instant createdAt, String createdBy, long version) {
    }

    public record CalendarItem(
            Long orderId, String orderNumber, String customerName, LocalDate date,
            CalendarDateType dateType, OrderStatus status) {
    }
}

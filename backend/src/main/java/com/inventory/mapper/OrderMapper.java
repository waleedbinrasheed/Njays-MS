package com.inventory.mapper;

import com.inventory.dto.order.OrderDtos.CalendarDateType;
import com.inventory.dto.order.OrderDtos.CalendarItem;
import com.inventory.dto.order.OrderDtos.CustomerRef;
import com.inventory.dto.order.OrderDtos.FabricRef;
import com.inventory.dto.order.OrderDtos.OrderLineMeasurementResponse;
import com.inventory.dto.order.OrderDtos.OrderLineResponse;
import com.inventory.dto.order.OrderDtos.OrderResponse;
import com.inventory.dto.order.OrderDtos.OrderSummary;
import com.inventory.dto.order.OrderDtos.PaymentResponse;
import com.inventory.dto.order.OrderDtos.StatusHistoryResponse;
import com.inventory.entity.CustomerOrder;
import com.inventory.entity.OrderLine;
import com.inventory.entity.OrderPayment;
import com.inventory.entity.OrderStatusHistory;
import com.inventory.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

import static com.inventory.mapper.MasterDataMapper.garmentRef;

public final class OrderMapper {

    private OrderMapper() {
    }

    /** UNPAID when nothing is paid, PAID when paid in full, otherwise PARTIAL. */
    public static PaymentStatus paymentStatus(BigDecimal total, BigDecimal paid) {
        if (paid.signum() == 0) return PaymentStatus.UNPAID;
        if (paid.compareTo(total) >= 0) return PaymentStatus.PAID;
        return PaymentStatus.PARTIAL;
    }

    public static OrderSummary toSummary(CustomerOrder o, BigDecimal paid) {
        return new OrderSummary(
                o.getId(),
                o.getOrderNumber(),
                o.getStatus(),
                o.getCustomer().getName(),
                o.getDueDate(),
                o.getTotal(),
                o.getTotal().subtract(paid),
                paymentStatus(o.getTotal(), paid),
                o.getLines().size());
    }

    public static OrderResponse toResponse(CustomerOrder o, String createdBy, List<OrderPayment> payments,
                                           List<OrderStatusHistory> history) {
        BigDecimal paid = payments.stream().map(OrderPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderResponse(
                o.getId(),
                o.getOrderNumber(),
                o.getStatus(),
                new CustomerRef(o.getCustomer().getId(), o.getCustomer().getName(), o.getCustomer().getPhone(),
                        o.getCustomer().getAddress()),
                o.getOrderDate(),
                o.getDueDate(),
                o.getTrialDate(),
                o.getDeliveryDate(),
                o.getNotes(),
                o.getTotal(),
                paid,
                o.getTotal().subtract(paid),
                paymentStatus(o.getTotal(), paid),
                o.getCancelReason(),
                o.getLines().stream().map(OrderMapper::toLineResponse).toList(),
                payments.stream().map(OrderMapper::toResponse).toList(),
                history.stream().map(OrderMapper::toResponse).toList(),
                o.getCreatedAt(),
                createdBy,
                o.getVersion());
    }

    public static OrderLineResponse toLineResponse(OrderLine l) {
        FabricRef fabric = l.getFabricProduct() == null ? null
                : new FabricRef(l.getFabricProduct().getId(), l.getFabricProduct().getFabricCode(),
                l.getFabricName(), l.getFabricProduct().getColour());
        return new OrderLineResponse(
                l.getId(),
                l.getLineNo(),
                garmentRef(l.getGarmentType()),
                l.getFabricSource(),
                fabric,
                l.getFabricMetres(),
                l.getCustomerFabricNote(),
                l.getFabricPricePerMetre(),
                l.getFabricAmount(),
                l.getStitchingCharge(),
                l.getLineTotal(),
                l.getMeasurementRecord() == null ? null : l.getMeasurementRecord().getId(),
                l.getMeasurements().stream()
                        .map(m -> new OrderLineMeasurementResponse(m.getPointLabel(), m.getValue(), m.getSortOrder()))
                        .toList(),
                l.getNotes());
    }

    public static PaymentResponse toResponse(OrderPayment p) {
        return new PaymentResponse(p.getId(), p.getKind(), p.getAmount(), p.getPaidOn(), p.getMethod(), p.getNote(),
                p.getRecordedBy() == null ? null : p.getRecordedBy().getFullName());
    }

    public static StatusHistoryResponse toResponse(OrderStatusHistory h) {
        return new StatusHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getChangedAt(),
                h.getChangedBy() == null ? null : h.getChangedBy().getFullName(), h.getNote());
    }

    public static CalendarItem calendarItem(CustomerOrder o, CalendarDateType type, java.time.LocalDate date) {
        return new CalendarItem(o.getId(), o.getOrderNumber(), o.getCustomer().getName(), date, type, o.getStatus());
    }
}

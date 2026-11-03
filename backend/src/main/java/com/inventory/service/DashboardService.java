package com.inventory.service;

import com.inventory.dto.report.ReportDtos.Dashboard;
import com.inventory.dto.report.ReportDtos.RecentOrder;
import com.inventory.entity.CustomerOrder;
import com.inventory.entity.Inventory;
import com.inventory.entity.OrderStatus;
import com.inventory.repository.CustomerOrderRepository;
import com.inventory.repository.InventoryRepository;
import com.inventory.repository.OrderPaymentRepository;
import com.inventory.security.AppUserPrincipal;
import com.inventory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Figures calculated live. Order figures are returned only to users with ORDER_VIEW and the low-stock
 * count only to users with INVENTORY_VIEW; other sections are null.
 */
@Service
public class DashboardService {

    private static final List<OrderStatus> CLOSED = List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
    private static final int RECENT = 8;

    private final CustomerOrderRepository orders;
    private final OrderPaymentRepository payments;
    private final InventoryRepository inventories;
    private final Clock clock;
    private final ZoneId zone;

    public DashboardService(CustomerOrderRepository orders, OrderPaymentRepository payments,
                            InventoryRepository inventories, Clock clock, ZoneId businessZone) {
        this.orders = orders;
        this.payments = payments;
        this.inventories = inventories;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Transactional(readOnly = true)
    public Dashboard load() {
        Set<String> can = CurrentUser.principal().map(AppUserPrincipal::getPermissions).orElse(Set.of());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        boolean ordersVisible = can.contains("ORDER_VIEW");
        boolean stockVisible = can.contains("INVENTORY_VIEW");

        Long dueToday = null;
        Long overdue = null;
        Long readyForDelivery = null;
        Map<OrderStatus, Long> inProgress = null;
        BigDecimal outstanding = null;
        Long ordersToday = null;
        List<RecentOrder> recent = null;
        if (ordersVisible) {
            dueToday = orders.countDueOn(today, CLOSED);
            overdue = orders.countOverdue(today, CLOSED);
            readyForDelivery = orders.countByStatus(OrderStatus.READY);
            inProgress = new EnumMap<>(OrderStatus.class);
            for (OrderStatus status : OrderStatus.PIPELINE) {
                inProgress.put(status, orders.countByStatus(status));
            }
            outstanding = orders.sumTotalsExcluding(OrderStatus.CANCELLED)
                    .subtract(payments.sumPaidOnLiveOrders());
            ordersToday = orders.countByOrderDate(today);
            recent = orders.findTop8ByOrderByIdDesc().stream().map(DashboardService::recentOrder).toList();
        }
        Long lowStock = stockVisible ? lowStockFabrics() : null;

        return new Dashboard(today, dueToday, overdue, readyForDelivery, inProgress, outstanding, ordersToday,
                recent, lowStock);
    }

    private long lowStockFabrics() {
        return inventories.findAllFabricStock().stream()
                .filter(i -> i.getProduct().isActive())
                .filter(Inventory::isLowStock)
                .count();
    }

    private static RecentOrder recentOrder(CustomerOrder o) {
        return new RecentOrder(o.getId(), o.getOrderNumber(), o.getCustomer().getName(), o.getStatus(), o.getDueDate());
    }
}

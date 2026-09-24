package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.inventory.InventoryDtos.InventoryItem;
import com.inventory.dto.inventory.InventoryDtos.InventorySummary;
import com.inventory.dto.inventory.InventoryDtos.StockTransactionResponse;
import com.inventory.entity.Inventory;
import com.inventory.entity.StockTransaction;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.InventoryMapper;
import com.inventory.repository.InventoryRepository;
import com.inventory.repository.InventorySpecifications.StockStatus;
import com.inventory.repository.StockTransactionRepository;
import com.inventory.repository.StockTransactionRepository.Filters;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static com.inventory.repository.InventorySpecifications.category;
import static com.inventory.repository.InventorySpecifications.product;
import static com.inventory.repository.InventorySpecifications.productActive;
import static com.inventory.repository.InventorySpecifications.productMatches;
import static com.inventory.repository.InventorySpecifications.publisher;
import static com.inventory.repository.InventorySpecifications.status;

/** Read-only views of stock: current inventory, summary figures and movement history. */
@Service
public class StockQueryService {

    private final InventoryRepository inventories;
    private final StockTransactionRepository transactions;
    private final ZoneId zone;

    public StockQueryService(InventoryRepository inventories, StockTransactionRepository transactions, ZoneId businessZone) {
        this.inventories = inventories;
        this.transactions = transactions;
        this.zone = businessZone;
    }

    public record InventoryFilter(String search, Long productId, Long categoryId, Long publisherId,
                                  StockStatus status, Boolean active) {
    }

    public record MovementFilter(Long productId, TransactionType type, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryItem> inventory(InventoryFilter f, Pageable pageable) {
        Specification<Inventory> spec = Specification.allOf(
                productMatches(f.search()), product(f.productId()), category(f.categoryId()),
                publisher(f.publisherId()), status(f.status()), productActive(f.active()));
        return PageResponse.of(inventories.findAll(spec, pageable), InventoryMapper::toItem);
    }

    @Transactional(readOnly = true)
    public InventoryItem item(Long productId) {
        return inventories.findDetailedByProductId(productId)
                .map(InventoryMapper::toItem)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    @Transactional(readOnly = true)
    public InventorySummary summary() {
        Object[] row = inventories.summaryRow();
        // Some JPA providers wrap a single aggregate row in an outer array.
        Object[] r = row.length == 1 && row[0] instanceof Object[] inner ? inner : row;
        return new InventorySummary(toLong(r[0]), toLong(r[1]), toLong(r[2]), toLong(r[3]));
    }

    /** Dates are business days in the shop's time zone; "to" is inclusive. */
    @Transactional(readOnly = true)
    public PageResponse<StockTransactionResponse> movements(MovementFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        Specification<StockTransaction> spec = Specification.allOf(
                Filters.product(f.productId()), Filters.type(f.type()),
                Filters.from(startOf(f.from())), Filters.before(startOf(f.to() == null ? null : f.to().plusDays(1))));
        return PageResponse.of(transactions.findAll(spec, pageable), InventoryMapper::toResponse);
    }

    private Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(zone).toInstant();
    }

    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    /** Exposed for services that need "today" in business time. */
    public static LocalDate today(Clock clock, ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }
}

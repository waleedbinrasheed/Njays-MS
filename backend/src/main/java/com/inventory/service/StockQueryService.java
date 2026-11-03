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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static com.inventory.repository.InventorySpecifications.fabricsOnly;
import static com.inventory.repository.InventorySpecifications.product;
import static com.inventory.repository.InventorySpecifications.productActive;
import static com.inventory.repository.InventorySpecifications.search;
import static com.inventory.repository.InventorySpecifications.status;

/** Read-only views of fabric stock: current quantities, summary figures and the movement ledger. */
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

    public record InventoryFilter(String search, Long productId, StockStatus status, Boolean active) {
    }

    public record MovementFilter(Long productId, TransactionType type, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryItem> inventory(InventoryFilter f, Pageable pageable) {
        Specification<Inventory> spec = Specification.allOf(
                fabricsOnly(), search(f.search()), product(f.productId()), status(f.status()), productActive(f.active()));
        return PageResponse.of(inventories.findAll(spec, pageable), InventoryMapper::toItem);
    }

    @Transactional(readOnly = true)
    public InventoryItem item(Long productId) {
        return inventories.findDetailedByProductId(productId)
                .filter(i -> i.getProduct().getProductType() == com.inventory.entity.ProductType.FABRIC)
                .map(InventoryMapper::toItem)
                .orElseThrow(() -> new ResourceNotFoundException("Fabric", productId));
    }

    /** Figures over active fabrics. */
    @Transactional(readOnly = true)
    public InventorySummary summary() {
        List<Inventory> rows = inventories.findAllFabricStock().stream()
                .filter(i -> i.getProduct().isActive())
                .toList();
        BigDecimal metres = rows.stream().map(Inventory::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        long low = rows.stream().filter(Inventory::isLowStock).count();
        long out = rows.stream().filter(i -> i.getQuantity().signum() == 0).count();
        return new InventorySummary(rows.size(), metres, low, out);
    }

    /** Dates are business days in the shop's time zone; "to" is inclusive. */
    @Transactional(readOnly = true)
    public PageResponse<StockTransactionResponse> movements(MovementFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        Specification<StockTransaction> spec = Specification.allOf(
                Filters.fabricsOnly(), Filters.product(f.productId()), Filters.type(f.type()),
                Filters.from(startOf(f.from())), Filters.before(startOf(f.to() == null ? null : f.to().plusDays(1))));
        return PageResponse.of(transactions.findAll(spec, pageable), InventoryMapper::toResponse);
    }

    private Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(zone).toInstant();
    }
}

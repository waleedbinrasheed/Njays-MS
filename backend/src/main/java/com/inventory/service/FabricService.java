package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.fabric.FabricDtos.FabricRequest;
import com.inventory.dto.fabric.FabricDtos.FabricResponse;
import com.inventory.entity.Inventory;
import com.inventory.entity.Product;
import com.inventory.entity.ProductType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.InventoryRepository;
import com.inventory.repository.InventorySpecifications;
import com.inventory.repository.InventorySpecifications.StockStatus;
import com.inventory.repository.OrderLineRepository;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.StockTransactionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import static com.inventory.repository.InventorySpecifications.fabricsOnly;
import static com.inventory.repository.InventorySpecifications.productActive;
import static com.inventory.repository.InventorySpecifications.status;
import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.deleteIfUnused;
import static com.inventory.service.MasterDataSupport.inUse;
import static com.inventory.service.MasterDataSupport.metres;
import static com.inventory.service.MasterDataSupport.money;
import static com.inventory.service.MasterDataSupport.trimToNull;

/**
 * Fabrics: products of type FABRIC with their stock row. Creating a fabric creates its stock row
 * (zero metres). Stock itself moves through {@link InventoryService} and {@link StockAdjustmentService}.
 */
@Service
public class FabricService {

    private final ProductRepository products;
    private final InventoryRepository inventories;
    private final InventoryService inventoryService;
    private final StockTransactionRepository stockTransactions;
    private final OrderLineRepository orderLines;
    private final Clock clock;

    public FabricService(ProductRepository products, InventoryRepository inventories, InventoryService inventoryService,
                         StockTransactionRepository stockTransactions, OrderLineRepository orderLines, Clock clock) {
        this.products = products;
        this.inventories = inventories;
        this.inventoryService = inventoryService;
        this.stockTransactions = stockTransactions;
        this.orderLines = orderLines;
        this.clock = clock;
    }

    public record FabricFilter(String search, Boolean active, boolean lowStockOnly) {
    }

    @Transactional(readOnly = true)
    public PageResponse<FabricResponse> search(FabricFilter f, Pageable pageable) {
        Specification<Inventory> spec = Specification.allOf(
                fabricsOnly(), InventorySpecifications.search(f.search()), productActive(f.active()),
                f.lowStockOnly() ? status(StockStatus.LOW) : null);
        return PageResponse.of(inventories.findAll(spec, pageable), MasterDataMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public FabricResponse get(Long id) {
        return MasterDataMapper.toResponse(loadStock(id));
    }

    @Transactional
    public FabricResponse create(FabricRequest request) {
        String code = request.code().trim();
        if (products.existsByFabricCodeIgnoreCase(code)) {
            throw duplicateCode(code);
        }
        Product product = new Product();
        apply(product, request, code);
        Product saved = products.save(product);
        Inventory stock = inventoryService.initialize(saved, reorderLevel(request.reorderLevel()));
        return MasterDataMapper.toResponse(stock);
    }

    @Transactional
    public FabricResponse update(Long id, FabricRequest request) {
        Inventory stock = loadStock(id);
        Product product = stock.getProduct();
        checkVersion(product, request.version());
        String code = request.code().trim();
        if (products.existsByFabricCodeIgnoreCaseAndIdNot(code, id)) {
            throw duplicateCode(code);
        }
        apply(product, request, code);
        stock.changeReorderLevel(reorderLevel(request.reorderLevel()), Instant.now(clock));
        products.saveAndFlush(product);
        inventories.flush();
        return MasterDataMapper.toResponse(stock);
    }

    /** Only a fabric never used in an order or stock movement can be deleted; otherwise deactivate it. */
    @Transactional
    public void delete(Long id) {
        Inventory stock = loadStock(id);
        Product product = stock.getProduct();
        String label = "Fabric '" + product.getTitle() + "'";
        if (stockTransactions.existsByProductId(id) || orderLines.existsByFabricProductId(id)) {
            throw inUse(label);
        }
        inventories.delete(stock);
        inventories.flush();
        deleteIfUnused(products, product, label);
    }

    private void apply(Product p, FabricRequest r, String code) {
        p.setFabricCode(code);
        p.setTitle(r.name().trim());
        p.setMaterial(trimToNull(r.material()));
        p.setColour(trimToNull(r.colour()));
        p.setPattern(trimToNull(r.pattern()));
        p.setWidth(trimToNull(r.width()));
        p.setPurchasePrice(money(r.costPerMetre()));
        p.setSellingPrice(money(r.pricePerMetre()));
        p.setActive(r.active() == null || r.active());
    }

    private static BigDecimal reorderLevel(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : metres(value);
    }

    private Inventory loadStock(Long id) {
        return inventories.findDetailedByProductId(id)
                .filter(i -> i.getProduct().getProductType() == ProductType.FABRIC)
                .orElseThrow(() -> new ResourceNotFoundException("Fabric", id));
    }

    private static BusinessException duplicateCode(String code) {
        return new BusinessException(HttpStatus.CONFLICT, "A fabric with code " + code + " already exists");
    }
}

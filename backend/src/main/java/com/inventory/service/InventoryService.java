package com.inventory.service;

import com.inventory.entity.AppUser;
import com.inventory.entity.Inventory;
import com.inventory.entity.Product;
import com.inventory.entity.ProductType;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.StockTransaction;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.InventoryRepository;
import com.inventory.repository.StockTransactionRepository;
import com.inventory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.inventory.service.MasterDataSupport.plain;

/**
 * The ONLY component allowed to change stock quantities. Quantities are decimal metres for fabrics.
 *
 * <p>Every movement, in one database transaction owned by the calling business operation:
 * <ol>
 *   <li>locks the product's inventory row (SELECT ... FOR UPDATE);</li>
 *   <li>validates it (non-zero change, fabric product, active product where new stock is cut,
 *       stock never below zero);</li>
 *   <li>updates the quantity;</li>
 *   <li>writes an immutable {@link StockTransaction} with previous and resulting quantities.</li>
 * </ol>
 * Methods use {@link Propagation#MANDATORY} so they cannot run outside the caller's transaction:
 * if the order or adjustment fails later, the stock change rolls back with it.
 */
@Service
public class InventoryService {

    private final InventoryRepository inventories;
    private final StockTransactionRepository transactions;
    private final AppUserRepository users;
    private final Clock clock;

    public InventoryService(InventoryRepository inventories, StockTransactionRepository transactions,
                            AppUserRepository users, Clock clock) {
        this.inventories = inventories;
        this.transactions = transactions;
        this.users = users;
        this.clock = clock;
    }

    /** One requested stock change. {@code quantityChange} is signed: +in, -out, in metres. */
    public record Movement(Long productId, TransactionType type, BigDecimal quantityChange,
                           ReferenceType referenceType, Long referenceId, String notes) {
    }

    /** Creates the stock row for a new fabric, with its reorder level and zero quantity. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Inventory initialize(Product product, BigDecimal reorderLevel) {
        Instant now = Instant.now(clock);
        Inventory stock = inventories.save(new Inventory(product, now));
        stock.changeReorderLevel(reorderLevel, now);
        return stock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockTransaction apply(Movement movement) {
        return applyAll(List.of(movement)).getFirst();
    }

    /**
     * Applies several movements atomically (e.g. all lines of one order). Rows are locked in
     * product-id order so two documents touching the same products cannot deadlock.
     * If any line is invalid, an exception is thrown and the whole transaction rolls back.
     * The returned ledger rows are in product-id order.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockTransaction> applyAll(List<Movement> movements) {
        if (movements.isEmpty()) {
            throw new IllegalArgumentException("No stock movements given");
        }
        AppUser actor = users.getReferenceById(CurrentUser.requireId());
        Instant now = Instant.now(clock);

        List<Movement> ordered = new ArrayList<>(movements);
        ordered.sort(Comparator.comparing(Movement::productId));

        List<StockTransaction> written = new ArrayList<>(ordered.size());
        for (Movement m : ordered) {
            written.add(applyOne(m, actor, now));
        }
        return written;
    }

    /** Throws if applying {@code change} would take the (locked) stock below zero. */
    public void requireAvailable(Inventory stock, BigDecimal change) {
        if (stock.getQuantity().add(change).signum() < 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Insufficient stock for '" + stock.getProduct().getTitle() + "': available "
                            + plain(stock.getQuantity()) + " m, requested " + plain(change.negate()) + " m");
        }
    }

    private StockTransaction applyOne(Movement m, AppUser actor, Instant now) {
        BigDecimal change = m.quantityChange();
        if (change == null || change.signum() == 0) {
            throw new BusinessException("Quantity must not be zero");
        }
        if (m.type() == TransactionType.ORDER_ISSUE && change.signum() > 0
                || m.type() == TransactionType.ORDER_CANCEL && change.signum() < 0) {
            throw new IllegalArgumentException("Sign of " + m.type() + " movement is wrong: " + change);
        }
        Inventory stock = inventories.lockByProductId(m.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Fabric", m.productId()));
        Product product = stock.getProduct();

        if (product.getProductType() != ProductType.FABRIC) {
            throw new BusinessException("'" + product.getTitle() + "' is not a fabric");
        }
        if (m.type() == TransactionType.ORDER_ISSUE && !product.isActive()) {
            throw new BusinessException("Fabric '" + product.getTitle() + "' is inactive");
        }

        BigDecimal previous = stock.getQuantity();
        requireAvailable(stock, change);

        stock.applyQuantityChange(change, now);
        return transactions.save(new StockTransaction(product, m.type(), change, previous,
                m.referenceType(), m.referenceId(), actor, now, m.notes()));
    }
}

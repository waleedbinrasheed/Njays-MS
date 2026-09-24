package com.inventory.mapper;

import com.inventory.dto.inventory.InventoryDtos.InventoryItem;
import com.inventory.dto.inventory.InventoryDtos.ProductRef;
import com.inventory.dto.inventory.InventoryDtos.StockAdjustmentResponse;
import com.inventory.dto.inventory.InventoryDtos.StockLevel;
import com.inventory.dto.inventory.InventoryDtos.StockTransactionResponse;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.entity.AppUser;
import com.inventory.entity.Inventory;
import com.inventory.entity.Product;
import com.inventory.entity.StockAdjustment;
import com.inventory.entity.StockTransaction;

public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static StockLevel levelOf(Inventory i) {
        if (i.getQuantity() == 0) return StockLevel.OUT_OF_STOCK;
        if (i.isLowStock()) return StockLevel.LOW;
        return StockLevel.OK;
    }

    public static InventoryItem toItem(Inventory i) {
        Product p = i.getProduct();
        return new InventoryItem(
                p.getId(),
                p.getIsbn(),
                p.getTitle(),
                p.getAuthor(),
                p.getPublisher() == null ? null : new Ref(p.getPublisher().getId(), p.getPublisher().getName()),
                p.getCategory() == null ? null : new Ref(p.getCategory().getId(), p.getCategory().getName()),
                i.getQuantity(),
                i.getReorderLevel(),
                levelOf(i),
                p.getPurchasePrice(),
                p.getSellingPrice(),
                p.isActive(),
                i.getUpdatedAt());
    }

    public static ProductRef productRef(Product p) {
        return new ProductRef(p.getId(), p.getTitle(), p.getIsbn());
    }

    public static Ref userRef(AppUser u) {
        return new Ref(u.getId(), u.getFullName());
    }

    public static StockTransactionResponse toResponse(StockTransaction t) {
        return new StockTransactionResponse(
                t.getId(),
                t.getCreatedAt(),
                productRef(t.getProduct()),
                t.getType(),
                t.getQuantity(),
                t.getPreviousQuantity(),
                t.getResultingQuantity(),
                t.getReferenceType(),
                t.getReferenceId(),
                userRef(t.getCreatedBy()),
                t.getNotes());
    }

    public static StockAdjustmentResponse toResponse(StockAdjustment a, Long stockTransactionId) {
        return new StockAdjustmentResponse(
                a.getId(),
                productRef(a.getProduct()),
                a.getReason(),
                a.getPreviousQuantity(),
                a.getAdjustmentQuantity(),
                a.getResultingQuantity(),
                a.getNotes(),
                userRef(a.getCreatedBy()),
                a.getCreatedAt(),
                stockTransactionId);
    }
}

package com.inventory.mapper;

import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.purchase.PurchaseDtos.PurchaseDetail;
import com.inventory.dto.purchase.PurchaseDtos.PurchaseLine;
import com.inventory.dto.purchase.PurchaseDtos.PurchaseSummary;
import com.inventory.entity.Purchase;
import com.inventory.entity.PurchaseItem;

public final class PurchaseMapper {

    private PurchaseMapper() {
    }

    public static PurchaseSummary toSummary(Purchase p, Ref createdBy) {
        return new PurchaseSummary(p.getId(), p.getPurchaseNumber(), p.getPurchaseDate(),
                new Ref(p.getSupplier().getId(), p.getSupplier().getName()), p.getSupplierInvoiceNumber(),
                p.getTotal(), p.getPaymentStatus(), p.getStatus(), createdBy, p.getCreatedAt());
    }

    public static PurchaseDetail toDetail(Purchase p, Ref createdBy) {
        return new PurchaseDetail(
                p.getId(),
                p.getPurchaseNumber(),
                p.getPurchaseDate(),
                new Ref(p.getSupplier().getId(), p.getSupplier().getName()),
                p.getSupplierInvoiceNumber(),
                p.getSubtotal(),
                p.getInvoiceDiscount(),
                p.getDiscountTotal(),
                p.getTaxTotal(),
                p.getTotal(),
                p.getPaymentStatus(),
                p.getStatus(),
                p.getNotes(),
                p.getItems().stream().map(PurchaseMapper::toLine).toList(),
                createdBy,
                p.getCreatedAt(),
                p.getCancelledAt(),
                p.getCancelledBy() == null ? null : InventoryMapper.userRef(p.getCancelledBy()),
                p.getCancelReason(),
                p.getVersion());
    }

    private static PurchaseLine toLine(PurchaseItem i) {
        return new PurchaseLine(i.getId(), i.getLineNo(), InventoryMapper.productRef(i.getProduct()), i.getQuantity(),
                i.getUnitCost(), i.getDiscountPercent(), i.getDiscountAmount(), i.getTaxPercent(), i.getTaxAmount(),
                i.getLineTotal());
    }
}

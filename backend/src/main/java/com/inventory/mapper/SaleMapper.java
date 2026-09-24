package com.inventory.mapper;

import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.sale.SaleDtos.SaleDetail;
import com.inventory.dto.sale.SaleDtos.SaleLine;
import com.inventory.dto.sale.SaleDtos.SaleSummary;
import com.inventory.entity.Sale;
import com.inventory.entity.SaleItem;

public final class SaleMapper {

    private SaleMapper() {
    }

    public static SaleSummary toSummary(Sale s, Ref createdBy) {
        return new SaleSummary(s.getId(), s.getInvoiceNumber(), s.getSaleDate(),
                new Ref(s.getCustomer().getId(), s.getCustomer().getName()), s.getTotal(), s.getPaymentStatus(),
                s.getStatus(), createdBy, s.getCreatedAt());
    }

    public static SaleDetail toDetail(Sale s, Ref createdBy) {
        return new SaleDetail(
                s.getId(),
                s.getInvoiceNumber(),
                s.getSaleDate(),
                new Ref(s.getCustomer().getId(), s.getCustomer().getName()),
                s.getCustomer().getPhone(),
                s.getCustomer().getAddress(),
                s.getSubtotal(),
                s.getInvoiceDiscount(),
                s.getDiscountTotal(),
                s.getTaxTotal(),
                s.getTotal(),
                s.getPaymentStatus(),
                s.getStatus(),
                s.getNotes(),
                s.getItems().stream().map(SaleMapper::toLine).toList(),
                createdBy,
                s.getCreatedAt(),
                s.getCancelledAt(),
                s.getCancelledBy() == null ? null : InventoryMapper.userRef(s.getCancelledBy()),
                s.getCancelReason(),
                s.getVersion());
    }

    private static SaleLine toLine(SaleItem i) {
        return new SaleLine(i.getId(), i.getLineNo(), InventoryMapper.productRef(i.getProduct()), i.getQuantity(),
                i.getUnitPrice(), i.getDiscountPercent(), i.getDiscountAmount(), i.getTaxPercent(), i.getTaxAmount(),
                i.getLineTotal());
    }
}

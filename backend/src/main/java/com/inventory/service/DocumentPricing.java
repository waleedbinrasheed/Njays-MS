package com.inventory.service;

import com.inventory.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Invoice arithmetic shared by purchases and sales (PKR, 2 decimals, half-up rounding):
 * <pre>
 *   line gross    = quantity x unit price
 *   line discount = round(gross x discount% / 100)
 *   line tax      = round((gross - line discount) x tax% / 100)
 *   line total    = gross - line discount + line tax
 *
 *   subtotal       = SUM(gross)
 *   discount total = SUM(line discount) + invoice discount (a fixed amount)
 *   tax total      = SUM(line tax)
 *   total          = subtotal - discount total + tax total      (never below zero)
 * </pre>
 * Tax is charged on the discounted line amount. The invoice discount does not change line tax.
 */
public final class DocumentPricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DocumentPricing() {
    }

    public record LineInput(int quantity, BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal taxPercent) {
    }

    public record LineAmounts(BigDecimal gross, BigDecimal discount, BigDecimal tax, BigDecimal total) {
    }

    public record Totals(BigDecimal subtotal, BigDecimal invoiceDiscount, BigDecimal discountTotal,
                         BigDecimal taxTotal, BigDecimal total) {
    }

    public static LineAmounts line(LineInput in) {
        BigDecimal gross = money(in.unitPrice().multiply(BigDecimal.valueOf(in.quantity())));
        BigDecimal discount = money(gross.multiply(orZero(in.discountPercent())).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        BigDecimal taxable = gross.subtract(discount);
        BigDecimal tax = money(taxable.multiply(orZero(in.taxPercent())).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        return new LineAmounts(gross, discount, tax, taxable.add(tax));
    }

    public static Totals totals(List<LineAmounts> lines, BigDecimal invoiceDiscount) {
        BigDecimal subtotal = sum(lines.stream().map(LineAmounts::gross).toList());
        BigDecimal lineDiscounts = sum(lines.stream().map(LineAmounts::discount).toList());
        BigDecimal tax = sum(lines.stream().map(LineAmounts::tax).toList());
        BigDecimal extra = money(orZero(invoiceDiscount));

        BigDecimal beforeInvoiceDiscount = subtotal.subtract(lineDiscounts).add(tax);
        if (extra.compareTo(beforeInvoiceDiscount) > 0) {
            throw new BusinessException("Invoice discount (" + extra.toPlainString()
                    + ") cannot be more than the amount before discount (" + beforeInvoiceDiscount.toPlainString() + ")");
        }
        BigDecimal discountTotal = lineDiscounts.add(extra);
        return new Totals(subtotal, extra, discountTotal, tax, subtotal.subtract(discountTotal).add(tax));
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

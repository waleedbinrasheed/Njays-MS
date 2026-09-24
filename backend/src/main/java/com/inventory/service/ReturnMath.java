package com.inventory.service;

import com.inventory.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Refund / credit amounts for returned lines. A returned unit is worth its share of the original
 * line total (after line discount and tax); returning the last units of a line uses the exact
 * remainder so all returns of a line always add up to that line's total. The invoice-level
 * discount is not apportioned to returns.
 */
final class ReturnMath {

    private ReturnMath() {
    }

    record Returned(int quantity, BigDecimal amount) {
        static final Returned NONE = new Returned(0, BigDecimal.ZERO);
    }

    static BigDecimal amountFor(int originalQuantity, BigDecimal originalLineTotal, Returned already, int quantity,
                                String productTitle) {
        int remaining = originalQuantity - already.quantity();
        if (quantity > remaining) {
            throw new BusinessException("Cannot return " + quantity + " of '" + productTitle + "': only " + remaining
                    + " of " + originalQuantity + " left to return");
        }
        if (quantity == remaining) {
            return originalLineTotal.subtract(already.amount()).setScale(2, RoundingMode.HALF_UP);
        }
        return originalLineTotal.multiply(BigDecimal.valueOf(quantity))
                .divide(BigDecimal.valueOf(originalQuantity), 2, RoundingMode.HALF_UP);
    }
}

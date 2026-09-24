package com.inventory.service;

import com.inventory.exception.BusinessException;
import com.inventory.service.DocumentPricing.LineAmounts;
import com.inventory.service.DocumentPricing.LineInput;
import com.inventory.service.DocumentPricing.Totals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentPricingTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void plainLine() {
        LineAmounts a = DocumentPricing.line(new LineInput(10, d("250.00"), null, null));
        assertThat(a.gross()).isEqualByComparingTo("2500.00");
        assertThat(a.discount()).isEqualByComparingTo("0");
        assertThat(a.tax()).isEqualByComparingTo("0");
        assertThat(a.total()).isEqualByComparingTo("2500.00");
    }

    @Test
    void discountThenTaxOnDiscountedAmount() {
        // 3 x 999.99 = 2999.97; 10% off = 300.00 (299.997 rounded); taxable 2699.97; 17% tax = 458.99 (458.9949)
        LineAmounts a = DocumentPricing.line(new LineInput(3, d("999.99"), d("10"), d("17")));
        assertThat(a.gross()).isEqualByComparingTo("2999.97");
        assertThat(a.discount()).isEqualByComparingTo("300.00");
        assertThat(a.tax()).isEqualByComparingTo("458.99");
        assertThat(a.total()).isEqualByComparingTo("3158.96");
        assertThat(a.total()).isEqualByComparingTo(a.gross().subtract(a.discount()).add(a.tax()));
    }

    @Test
    void totalsWithInvoiceDiscount() {
        List<LineAmounts> lines = List.of(
                DocumentPricing.line(new LineInput(2, d("500"), d("10"), d("0"))),   // 1000 - 100
                DocumentPricing.line(new LineInput(1, d("300"), d("0"), d("5"))));    // 300 + 15
        Totals t = DocumentPricing.totals(lines, d("15"));

        assertThat(t.subtotal()).isEqualByComparingTo("1300.00");
        assertThat(t.invoiceDiscount()).isEqualByComparingTo("15.00");
        assertThat(t.discountTotal()).isEqualByComparingTo("115.00");
        assertThat(t.taxTotal()).isEqualByComparingTo("15.00");
        assertThat(t.total()).isEqualByComparingTo("1200.00");
    }

    @Test
    void fullDiscountGivesZeroTotal() {
        Totals t = DocumentPricing.totals(List.of(DocumentPricing.line(new LineInput(1, d("100"), d("100"), null))), null);
        assertThat(t.total()).isEqualByComparingTo("0");
    }

    @Test
    void invoiceDiscountCannotExceedAmount() {
        List<LineAmounts> lines = List.of(DocumentPricing.line(new LineInput(1, d("100"), null, null)));
        assertThatThrownBy(() -> DocumentPricing.totals(lines, d("100.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be more than");
    }
}

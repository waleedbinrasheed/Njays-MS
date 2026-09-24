/**
 * Live preview of invoice totals while a document is being entered. It mirrors the backend's
 * DocumentPricing exactly (half-up rounding to paisa), but the backend recalculates on save and
 * its figures are the ones stored and printed.
 */

export interface PricedLineInput {
  quantity: number;
  unitPrice: number;
  discountPercent: number;
  taxPercent: number;
}

export interface LinePreview {
  gross: number;
  discount: number;
  tax: number;
  total: number;
}

export interface TotalsPreview {
  subtotal: number;
  lineDiscounts: number;
  invoiceDiscount: number;
  discountTotal: number;
  taxTotal: number;
  total: number;
  /** Invoice discount larger than the amount it applies to (the backend rejects this). */
  invoiceDiscountTooLarge: boolean;
}

/** Work in whole paisa so floating point never drifts. */
const toPaisa = (rupees: number) => Math.round((Number.isFinite(rupees) ? rupees : 0) * 100);
const toRupees = (paisa: number) => paisa / 100;
const percentOf = (paisa: number, percent: number) => Math.round((paisa * (Number.isFinite(percent) ? percent : 0)) / 100);

export function previewLine(line: PricedLineInput): LinePreview {
  const gross = toPaisa(line.unitPrice) * (Number.isFinite(line.quantity) ? line.quantity : 0);
  const discount = percentOf(gross, line.discountPercent);
  const tax = percentOf(gross - discount, line.taxPercent);
  return { gross: toRupees(gross), discount: toRupees(discount), tax: toRupees(tax), total: toRupees(gross - discount + tax) };
}

export function previewTotals(lines: PricedLineInput[], invoiceDiscount: number): TotalsPreview {
  let subtotal = 0;
  let lineDiscounts = 0;
  let tax = 0;
  for (const line of lines) {
    const p = previewLine(line);
    subtotal += toPaisa(p.gross);
    lineDiscounts += toPaisa(p.discount);
    tax += toPaisa(p.tax);
  }
  const extra = toPaisa(invoiceDiscount);
  const beforeInvoiceDiscount = subtotal - lineDiscounts + tax;
  return {
    subtotal: toRupees(subtotal),
    lineDiscounts: toRupees(lineDiscounts),
    invoiceDiscount: toRupees(extra),
    discountTotal: toRupees(lineDiscounts + extra),
    taxTotal: toRupees(tax),
    total: toRupees(Math.max(beforeInvoiceDiscount - extra, 0)),
    invoiceDiscountTooLarge: extra > beforeInvoiceDiscount,
  };
}

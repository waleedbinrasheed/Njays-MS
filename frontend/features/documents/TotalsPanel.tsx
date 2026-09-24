"use client";

import type { TotalsPreview } from "@/utils/documentTotals";
import { formatCurrency } from "@/utils/format";

interface TotalsPanelProps {
  totals: TotalsPreview;
  invoiceDiscount: string;
  onInvoiceDiscountChange: (value: string) => void;
}

export function TotalsPanel({ totals, invoiceDiscount, onInvoiceDiscountChange }: TotalsPanelProps) {
  const row = "flex items-center justify-between gap-4 py-1";
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 text-sm">
      <div className={row}>
        <span className="text-slate-600">Subtotal</span>
        <span className="tabular-nums">{formatCurrency(totals.subtotal)}</span>
      </div>
      <div className={row}>
        <span className="text-slate-600">Line discounts</span>
        <span className="tabular-nums">− {formatCurrency(totals.lineDiscounts)}</span>
      </div>
      <div className={row}>
        <span className="text-slate-600">Tax</span>
        <span className="tabular-nums">+ {formatCurrency(totals.taxTotal)}</span>
      </div>
      <div className={row}>
        <label htmlFor="invoice-discount" className="text-slate-600">
          Invoice discount (PKR)
        </label>
        <input
          id="invoice-discount"
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          placeholder="0"
          value={invoiceDiscount}
          onChange={(e) => onInvoiceDiscountChange(e.target.value)}
          className={`w-32 rounded border px-2 py-1 text-right tabular-nums focus:outline-2 focus:outline-blue-500 ${
            totals.invoiceDiscountTooLarge ? "border-red-400" : "border-slate-300"
          }`}
        />
      </div>
      {totals.invoiceDiscountTooLarge && (
        <p className="text-right text-xs text-red-600">Discount is larger than the amount due</p>
      )}
      <div className={`${row} mt-2 border-t border-slate-200 pt-3 text-base font-semibold text-slate-900`}>
        <span>Total</span>
        <span className="tabular-nums">{formatCurrency(totals.total)}</span>
      </div>
      <p className="mt-1 text-right text-xs text-slate-400">Final amounts are calculated by the server when saved.</p>
    </div>
  );
}

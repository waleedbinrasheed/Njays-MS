"use client";

import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { EmptyState } from "@/components/ui/Feedback";
import { loadActiveProductOptions } from "@/features/inventory/lookups";
import type { InventoryItem } from "@/types/inventory";
import { previewLine } from "@/utils/documentTotals";
import { formatCurrency } from "@/utils/format";

/** A line being entered. Numbers are kept as the typed text so fields can be cleared while editing. */
export interface DraftLine {
  productId: number;
  title: string;
  isbn: string | null;
  stock: number;
  /** The book's selling price when the line was added (samples may not be priced above it). */
  sellingPrice?: number;
  quantity: string;
  unitPrice: string;
  discountPercent: string;
  taxPercent: string;
}

export const num = (s: string) => (s.trim() === "" ? 0 : Number(s));

export function toPricedLine(l: DraftLine) {
  return { quantity: num(l.quantity), unitPrice: num(l.unitPrice), discountPercent: num(l.discountPercent), taxPercent: num(l.taxPercent) };
}

interface LineItemsEditorProps {
  lines: DraftLine[];
  onChange: (lines: DraftLine[]) => void;
  /** "Unit cost" for purchases, "Unit price" for sales. */
  priceLabel: string;
  /** Default price for a newly added product. */
  defaultPrice: (item: InventoryItem) => number;
  /** Per-line problems from the backend or local checks, keyed by productId. */
  lineErrors?: Record<number, string>;
  /** Sales show stock and warn when quantity exceeds it. */
  showStockWarning?: boolean;
  /** Samples: no discount/tax columns, and the price may not exceed the selling price. */
  simple?: boolean;
}

/**
 * Fast multi-line entry: type or scan an ISBN / title in the search box and press Enter to add;
 * choosing an already-added product increases its quantity instead of adding a duplicate line.
 */
export function LineItemsEditor({
  lines,
  onChange,
  priceLabel,
  defaultPrice,
  lineErrors = {},
  showStockWarning = false,
  simple = false,
}: LineItemsEditorProps) {
  function add(option: Option | null) {
    const item = option?.data as InventoryItem | undefined;
    if (!item) return;
    const existing = lines.find((l) => l.productId === item.productId);
    if (existing) {
      onChange(lines.map((l) => (l.productId === item.productId ? { ...l, quantity: String(num(l.quantity) + 1) } : l)));
    } else {
      onChange([
        ...lines,
        {
          productId: item.productId,
          title: item.title,
          isbn: item.isbn,
          stock: item.quantity,
          sellingPrice: item.sellingPrice,
          quantity: "1",
          unitPrice: String(defaultPrice(item)),
          discountPercent: "",
          taxPercent: "",
        },
      ]);
    }
  }

  const update = (productId: number, patch: Partial<DraftLine>) =>
    onChange(lines.map((l) => (l.productId === productId ? { ...l, ...patch } : l)));
  const remove = (productId: number) => onChange(lines.filter((l) => l.productId !== productId));

  const cell = "w-full rounded border border-slate-300 px-2 py-1 text-right text-sm tabular-nums focus:outline-2 focus:outline-blue-500";

  return (
    <div className="space-y-3">
      <div className="max-w-xl">
        <AsyncSelect
          label="Add product"
          value={null}
          onChange={add}
          load={loadActiveProductOptions}
          placeholder="Type or scan ISBN, title or author, then press Enter"
          clearable={false}
          keepOpenOnSelect
          selectedIds={lines.map((l) => l.productId)}
        />
      </div>

      <div className="overflow-x-auto rounded-lg border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-3 py-2 font-medium">#</th>
              <th scope="col" className="px-3 py-2 font-medium">Product</th>
              <th scope="col" className="w-24 px-3 py-2 text-right font-medium">Qty</th>
              <th scope="col" className="w-32 px-3 py-2 text-right font-medium">{priceLabel}</th>
              {!simple && (
                <>
                  <th scope="col" className="w-24 px-3 py-2 text-right font-medium">Disc %</th>
                  <th scope="col" className="w-24 px-3 py-2 text-right font-medium">Tax %</th>
                </>
              )}
              <th scope="col" className="w-32 px-3 py-2 text-right font-medium">Line total</th>
              <th scope="col" className="w-10 px-3 py-2">
                <span className="sr-only">Remove</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {lines.map((l, index) => {
              const preview = previewLine(toPricedLine(l));
              const overStock = showStockWarning && num(l.quantity) > l.stock;
              const overPrice = simple && l.sellingPrice !== undefined && num(l.unitPrice) > l.sellingPrice;
              const error = lineErrors[l.productId];
              return (
                <tr key={l.productId} className="align-top">
                  <td className="px-3 py-2 text-slate-500">{index + 1}</td>
                  <td className="px-3 py-2">
                    <div className="font-medium text-slate-900">{l.title}</div>
                    <div className="text-xs text-slate-500">
                      {l.isbn ?? "No ISBN"} · In stock: {l.stock}
                    </div>
                    {overStock && <div className="text-xs font-medium text-red-600">Only {l.stock} in stock</div>}
                    {simple && l.sellingPrice !== undefined && (
                      <div className={`text-xs ${overPrice ? "font-medium text-red-600" : "text-slate-500"}`}>
                        {overPrice ? "Above the " : ""}selling price {formatCurrency(l.sellingPrice)}
                      </div>
                    )}
                    {error && <div className="text-xs font-medium text-red-600">{error}</div>}
                  </td>
                  <td className="px-3 py-2">
                    <input
                      aria-label={`Quantity of ${l.title}`}
                      type="number"
                      inputMode="numeric"
                      min="1"
                      step="1"
                      className={`${cell} ${overStock || num(l.quantity) < 1 ? "border-red-400" : ""}`}
                      value={l.quantity}
                      onChange={(e) => update(l.productId, { quantity: e.target.value })}
                    />
                  </td>
                  <td className="px-3 py-2">
                    <input
                      aria-label={`${priceLabel} of ${l.title}`}
                      type="number"
                      inputMode="decimal"
                      min="0"
                      step="0.01"
                      className={`${cell} ${overPrice ? "border-red-400" : ""}`}
                      value={l.unitPrice}
                      onChange={(e) => update(l.productId, { unitPrice: e.target.value })}
                    />
                  </td>
                  {!simple && (
                    <>
                      <td className="px-3 py-2">
                        <input
                          aria-label={`Discount percent of ${l.title}`}
                          type="number"
                          inputMode="decimal"
                          min="0"
                          max="100"
                          step="0.01"
                          placeholder="0"
                          className={cell}
                          value={l.discountPercent}
                          onChange={(e) => update(l.productId, { discountPercent: e.target.value })}
                        />
                      </td>
                      <td className="px-3 py-2">
                        <input
                          aria-label={`Tax percent of ${l.title}`}
                          type="number"
                          inputMode="decimal"
                          min="0"
                          max="100"
                          step="0.01"
                          placeholder="0"
                          className={cell}
                          value={l.taxPercent}
                          onChange={(e) => update(l.productId, { taxPercent: e.target.value })}
                        />
                      </td>
                    </>
                  )}
                  <td className="whitespace-nowrap px-3 py-2 text-right font-medium tabular-nums text-slate-900">
                    {formatCurrency(preview.total)}
                  </td>
                  <td className="px-3 py-2 text-right">
                    <button
                      type="button"
                      onClick={() => remove(l.productId)}
                      className="rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-600"
                      aria-label={`Remove ${l.title}`}
                    >
                      ×
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {lines.length === 0 && <EmptyState title="No items yet" description="Use the search box above to add products." />}
      </div>
    </div>
  );
}

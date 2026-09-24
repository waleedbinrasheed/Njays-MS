"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Badge, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SelectField, TextField } from "@/components/ui/FormField";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useApiQuery } from "@/hooks/useApiQuery";
import { inventoryService } from "@/services/inventoryService";
import type { StockTransaction, TransactionType } from "@/types/inventory";
import { formatDateTime } from "@/utils/format";
import { TRANSACTION_TYPE_LABELS, formatSignedQuantity } from "@/utils/labels";
import { loadStockedProductOptions } from "./lookups";

const PAGE_SIZE = 50;

function referenceLabel(t: StockTransaction): string {
  if (!t.referenceType || t.referenceId === null) return "-";
  const prefix = { ADJUSTMENT: "ADJ", PURCHASE: "PUR", SALE: "INV", SALE_RETURN: "SRT", PURCHASE_RETURN: "PRT" }[t.referenceType];
  return `${prefix}-${t.referenceId}`;
}

/** Read-only stock ledger with product, type and date filters. */
export function StockMovementsPage() {
  const params = useSearchParams();
  const initialProductId = Number(params.get("productId")) || null;

  const [product, setProduct] = useState<Option | null>(null);
  const [type, setType] = useState<TransactionType | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);

  // A product id arriving in the URL (from the Inventory "History" link) is resolved to its name.
  const linked = useApiQuery(
    (signal) => (initialProductId ? inventoryService.get(initialProductId, signal) : Promise.resolve(null)),
    [initialProductId],
  );
  const [linkDismissed, setLinkDismissed] = useState(false);
  const effectiveProduct =
    product ?? (!linkDismissed && linked.data ? { id: linked.data.productId, label: linked.data.title } : null);

  const dateError = from && to && to < from ? "'To' must be on or after 'From'" : undefined;

  const movements = useApiQuery(
    (signal) =>
      dateError
        ? Promise.resolve(null)
        : inventoryService.movements(
            {
              productId: effectiveProduct?.id,
              type: type || undefined,
              from: from || undefined,
              to: to || undefined,
              page,
              size: PAGE_SIZE,
            },
            signal,
          ),
    [effectiveProduct?.id ?? null, type, from, to, page, dateError ?? null],
  );

  const rows = movements.data?.content ?? [];
  const reset = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(0);
  };

  return (
    <>
      <PageHeader title="Stock Movement" description="Every change to stock, newest first. History cannot be edited." />

      <div className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-4">
        <AsyncSelect
          label="Product"
          value={effectiveProduct}
          onChange={(o) => {
            setProduct(o);
            setLinkDismissed(true);
            setPage(0);
          }}
          load={loadStockedProductOptions}
          placeholder="All products"
        />
        <SelectField label="Type" value={type} onChange={(e) => reset(setType)(e.target.value as TransactionType | "")}>
          <option value="">All types</option>
          {Object.entries(TRANSACTION_TYPE_LABELS).map(([k, label]) => (
            <option key={k} value={k}>
              {label}
            </option>
          ))}
        </SelectField>
        <TextField label="From" type="date" value={from} onChange={(e) => reset(setFrom)(e.target.value)} />
        <TextField label="To" type="date" value={to} onChange={(e) => reset(setTo)(e.target.value)} error={dateError} />
      </div>

      {movements.error && (
        <div className="mb-4">
          <ErrorBanner message={movements.error} onRetry={movements.reload} />
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-4 py-2.5 font-medium">Date</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Product</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Type</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Quantity</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Before</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">After</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Reference</th>
                <th scope="col" className="px-4 py-2.5 font-medium">User</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((t) => (
                <tr key={t.id} className="align-top hover:bg-slate-50">
                  <td className="whitespace-nowrap px-4 py-2.5 text-slate-600">{formatDateTime(t.createdAt)}</td>
                  <td className="px-4 py-2.5">
                    <div className="font-medium text-slate-900">{t.product.title}</div>
                    {t.notes && <div className="text-xs text-slate-500">{t.notes}</div>}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5">
                    <Badge tone={t.quantity > 0 ? "green" : "red"}>{TRANSACTION_TYPE_LABELS[t.type]}</Badge>
                  </td>
                  <td
                    className={`whitespace-nowrap px-4 py-2.5 text-right font-semibold tabular-nums ${
                      t.quantity > 0 ? "text-emerald-700" : "text-red-600"
                    }`}
                  >
                    {formatSignedQuantity(t.quantity)}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-600">{t.previousQuantity}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-900">{t.resultingQuantity}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 font-mono text-xs">{referenceLabel(t)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5">{t.user.name}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {movements.loading && !movements.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading stock movements..." />
          </div>
        )}
        {!movements.loading && movements.data && rows.length === 0 && (
          <EmptyState title="No stock movements" description="Nothing matches these filters yet." />
        )}
        {movements.data && (
          <Pagination
            page={movements.data.page}
            size={movements.data.size}
            totalElements={movements.data.totalElements}
            totalPages={movements.data.totalPages}
            onPageChange={setPage}
          />
        )}
      </div>
    </>
  );
}

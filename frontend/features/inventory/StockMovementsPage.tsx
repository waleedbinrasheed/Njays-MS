"use client";

import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SelectField, TextField } from "@/components/ui/FormField";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { loadFabricOptions } from "@/features/master/lookups";
import { useApiQuery } from "@/hooks/useApiQuery";
import { fabricService } from "@/services/masterDataService";
import { inventoryService } from "@/services/inventoryService";
import type { TransactionType } from "@/types/inventory";
import { formatDateTime } from "@/utils/format";
import { TRANSACTION_TYPE_LABELS, formatSignedQuantity } from "@/utils/labels";

const PAGE_SIZE = 50;
const TYPES = Object.keys(TRANSACTION_TYPE_LABELS) as TransactionType[];

/** Read-only fabric stock ledger with fabric, type and date filters. */
export function StockMovementsPage() {
  const params = useSearchParams();
  const initialProductId = Number(params.get("productId")) || null;

  const [fabric, setFabric] = useState<Option | null>(null);
  const [type, setType] = useState<TransactionType | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);

  // A fabric id arriving in the URL (from the Fabric Stock "History" link) is resolved to its name.
  const linked = useApiQuery(
    (signal) => (initialProductId ? fabricService.get(initialProductId, signal) : Promise.resolve(null)),
    [initialProductId],
  );
  const [linkDismissed, setLinkDismissed] = useState(false);
  const effectiveFabric: Option | null =
    fabric ?? (!linkDismissed && linked.data ? { id: linked.data.id, label: `${linked.data.code} · ${linked.data.name}` } : null);

  const dateError = from && to && to < from ? "'To' must be on or after 'From'" : undefined;

  const movements = useApiQuery(
    (signal) =>
      dateError
        ? Promise.resolve(undefined)
        : inventoryService.movements(
            {
              productId: effectiveFabric?.id,
              type: type || undefined,
              from: from || undefined,
              to: to || undefined,
              page,
              size: PAGE_SIZE,
            },
            signal,
          ),
    [effectiveFabric?.id ?? null, type, from, to, dateError, page],
  );

  const rows = movements.data?.content ?? [];

  return (
    <>
      <PageHeader title="Stock Movement" description="Every change to fabric stock, newest first." />

      <div className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-5">
        <div className="lg:col-span-2">
          <AsyncSelect
            label="Fabric"
            value={effectiveFabric}
            onChange={(o) => {
              setFabric(o);
              setLinkDismissed(true);
              setPage(0);
            }}
            load={loadFabricOptions}
            placeholder="All fabrics"
          />
        </div>
        <SelectField
          label="Type"
          value={type}
          onChange={(e) => {
            setType(e.target.value as TransactionType | "");
            setPage(0);
          }}
        >
          <option value="">All types</option>
          {TYPES.map((t) => (
            <option key={t} value={t}>
              {TRANSACTION_TYPE_LABELS[t]}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Date from"
          type="date"
          value={from}
          onChange={(e) => {
            setFrom(e.target.value);
            setPage(0);
          }}
        />
        <TextField
          label="Date to"
          type="date"
          value={to}
          onChange={(e) => {
            setTo(e.target.value);
            setPage(0);
          }}
          error={dateError}
        />
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
                <th scope="col" className="px-4 py-2.5 font-medium">Fabric</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Type</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Change (m)</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Resulting (m)</th>
                <th scope="col" className="px-4 py-2.5 font-medium">User</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Notes</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((t) => (
                <tr key={t.id} className="align-top">
                  <td className="whitespace-nowrap px-4 py-2.5 text-slate-600">{formatDateTime(t.createdAt)}</td>
                  <td className="px-4 py-2.5">
                    <span className="font-medium text-slate-900">{t.product.name}</span>
                    <span className="block font-mono text-xs text-slate-500">{t.product.code}</span>
                  </td>
                  <td className="px-4 py-2.5">{TRANSACTION_TYPE_LABELS[t.type]}</td>
                  <td className={`px-4 py-2.5 text-right font-semibold tabular-nums ${t.change > 0 ? "text-emerald-700" : "text-red-600"}`}>
                    {formatSignedQuantity(t.change)}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{t.resulting.toFixed(3)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5">{t.user.name}</td>
                  <td className="px-4 py-2.5 text-slate-600">{t.notes ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {movements.loading && !movements.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading movements..." />
          </div>
        )}
        {!movements.loading && movements.data && rows.length === 0 && <EmptyState title="No stock movements match these filters" />}
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

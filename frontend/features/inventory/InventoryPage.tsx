"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { Badge, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SearchInput, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { ApiError } from "@/services/apiClient";
import { inventoryService } from "@/services/inventoryService";
import type { InventoryRow } from "@/types/inventory";
import { formatCurrency } from "@/utils/format";

const PAGE_SIZE = 25;

export function InventoryPage() {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canAdjust = hasPermission("STOCK_ADJUST");

  const [search, setSearch] = useState("");
  const [lowOnly, setLowOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [reordering, setReordering] = useState<InventoryRow | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const debouncedSearch = useDebouncedValue(search);

  const list = useApiQuery(
    (signal) => inventoryService.list({ search: debouncedSearch, lowStockOnly: lowOnly || undefined, page, size: PAGE_SIZE, sort: "code,asc" }, signal),
    [debouncedSearch, lowOnly, page, refreshKey],
  );
  const rows = list.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Fabric Stock"
        description="Metres of fabric on hand. Cloth comes into stock through a stock-in adjustment; orders take it out when they are made."
        actions={
          canAdjust ? (
            <Link href="/inventory/adjustments" className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
              Stock adjustment
            </Link>
          ) : undefined
        }
      />

      <div className="mb-4 flex flex-col flex-wrap gap-2 sm:flex-row sm:items-center">
        <SearchInput
          placeholder="Search code, name or colour"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label="Search fabric stock"
        />
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            className="accent-blue-600"
            checked={lowOnly}
            onChange={(e) => {
              setLowOnly(e.target.checked);
              setPage(0);
            }}
          />
          Low stock only
        </label>
        {list.loading && list.data && <Spinner size="sm" />}
      </div>

      {list.error && (
        <div className="mb-4">
          <ErrorBanner message={list.error} onRetry={list.reload} />
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-4 py-2.5 font-medium">Code</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Fabric</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">On hand (m)</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Reorder at (m)</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Cost value</th>
                <th scope="col" className="px-4 py-2.5">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((r) => (
                <tr key={r.productId} className="align-top">
                  <td className="whitespace-nowrap px-4 py-2.5 font-mono text-xs">{r.code}</td>
                  <td className="px-4 py-2.5">
                    <div className="font-medium text-slate-900">{r.name}</div>
                    <div className="text-xs text-slate-500">{[r.colour, r.material].filter(Boolean).join(" · ") || "-"}</div>
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                    {r.quantityOnHand.toFixed(3)}{" "}
                    {r.quantityOnHand <= 0 ? <Badge tone="red">Out</Badge> : r.lowStock ? <Badge tone="amber">Low</Badge> : null}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                    {r.reorderLevel.toFixed(3)}
                    {canAdjust && (
                      <button type="button" className="ml-2 text-xs text-blue-700 hover:underline" onClick={() => setReordering(r)}>
                        Change
                      </button>
                    )}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{formatCurrency(r.costValue)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right text-sm">
                    <Link href={`/inventory/movements?productId=${r.productId}`} className="text-blue-700 hover:underline">
                      History
                    </Link>
                    {canAdjust && (
                      <Link href={`/inventory/adjustments?productId=${r.productId}`} className="ml-3 text-blue-700 hover:underline">
                        Adjust
                      </Link>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {list.loading && !list.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading fabric stock..." />
          </div>
        )}
        {!list.loading && list.data && rows.length === 0 && (
          <EmptyState title="No fabrics found" description={search || lowOnly ? "Try a different search or filter." : undefined} />
        )}
        {list.data && (
          <Pagination
            page={list.data.page}
            size={list.data.size}
            totalElements={list.data.totalElements}
            totalPages={list.data.totalPages}
            onPageChange={setPage}
          />
        )}
      </div>

      {reordering && (
        <ReorderLevelDialog
          row={reordering}
          onClose={() => setReordering(null)}
          onSaved={() => {
            setReordering(null);
            toast.success(`Reorder level for "${reordering.name}" saved`);
            setRefreshKey((k) => k + 1);
          }}
        />
      )}
    </>
  );
}

function ReorderLevelDialog({ row, onClose, onSaved }: { row: InventoryRow; onClose: () => void; onSaved: () => void }) {
  const [level, setLevel] = useState(String(row.reorderLevel));
  const [error, setError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const value = Number(level);
    if (level.trim() === "" || !Number.isFinite(value) || value < 0 || !/^\d+(\.\d{1,3})?$/.test(level.trim())) {
      setError("Enter metres of zero or more, up to 3 decimals");
      return;
    }
    setSaving(true);
    try {
      await inventoryService.setReorderLevel(row.productId, value, row.version);
      onSaved();
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not save the reorder level");
      setSaving(false);
    }
  }

  return (
    <FormDialog title={`Reorder level · ${row.name}`} submitLabel="Save" saving={saving} formError={formError} onSubmit={submit} onClose={onClose}>
      <TextField
        label="Reorder at (metres)"
        type="number"
        inputMode="decimal"
        min="0"
        step="0.001"
        value={level}
        onChange={(e) => setLevel(e.target.value)}
        error={error}
        required
        autoFocus
      />
    </FormDialog>
  );
}

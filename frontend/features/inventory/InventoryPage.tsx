"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { Badge, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SearchInput, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { Modal } from "@/components/ui/Modal";
import { Pagination } from "@/components/ui/Pagination";
import { SortableHeader, nextSort, toSortParam, type SortState } from "@/components/ui/SortableHeader";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { FilterSelect } from "@/features/master/FilterSelect";
import { loadCategoryOptions, loadPublisherOptions } from "@/features/master/lookups";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { ApiError } from "@/services/apiClient";
import { inventoryService } from "@/services/inventoryService";
import type { InventoryItem, StockLevel, StockStatusFilter } from "@/types/inventory";
import { formatCurrency } from "@/utils/format";
import { STOCK_LEVEL_LABELS, formatSignedQuantity } from "@/utils/labels";
import { AdjustmentForm } from "./AdjustmentForm";

const PAGE_SIZE = 25;
const LEVEL_TONE: Record<StockLevel, "red" | "amber" | "green"> = { OUT_OF_STOCK: "red", LOW: "amber", OK: "green" };

export function InventoryPage() {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canAdjust = hasPermission("STOCK_ADJUST");

  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<StockStatusFilter | "">("");
  const [category, setCategory] = useState<Option | null>(null);
  const [publisher, setPublisher] = useState<Option | null>(null);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>({ field: "title", direction: "asc" });
  const [adjusting, setAdjusting] = useState<InventoryItem | null>(null);
  const [reordering, setReordering] = useState<InventoryItem | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const debouncedSearch = useDebouncedValue(search);

  const list = useApiQuery(
    (signal) =>
      inventoryService.list(
        {
          search: debouncedSearch,
          status: status || undefined,
          categoryId: category?.id,
          publisherId: publisher?.id,
          active: true,
          page,
          size: PAGE_SIZE,
          sort: toSortParam(sort),
        },
        signal,
      ),
    [debouncedSearch, status, category?.id ?? null, publisher?.id ?? null, page, sort, refreshKey],
  );
  const summary = useApiQuery((signal) => inventoryService.summary(signal), [refreshKey]);

  const refresh = () => setRefreshKey((k) => k + 1);
  const onSort = (field: string) => {
    setSort((s) => nextSort(s, field));
    setPage(0);
  };
  const resetPage = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(0);
  };

  const rows = list.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Inventory"
        description="Current stock of every active product. Stock changes only through purchases, sales, returns and adjustments."
        actions={
          canAdjust ? (
            <Link href="/inventory/adjustments" className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
              New adjustment
            </Link>
          ) : undefined
        }
      />

      <div className="mb-5 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <SummaryCard label="Products" value={summary.data?.totalProducts} />
        <SummaryCard label="Units in stock" value={summary.data?.totalQuantity} />
        <SummaryCard
          label="Low stock"
          value={summary.data?.lowStockProducts}
          tone="amber"
          onClick={() => resetPage(setStatus)("LOW")}
        />
        <SummaryCard
          label="Out of stock"
          value={summary.data?.outOfStockProducts}
          tone="red"
          onClick={() => resetPage(setStatus)("OUT")}
        />
      </div>

      <div className="mb-4 flex flex-col flex-wrap gap-2 sm:flex-row sm:items-center">
        <SearchInput
          placeholder="Search ISBN, title or author"
          value={search}
          onChange={(e) => resetPage(setSearch)(e.target.value)}
          aria-label="Search inventory"
        />
        <select
          value={status}
          onChange={(e) => resetPage(setStatus)(e.target.value as StockStatusFilter | "")}
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
          aria-label="Stock status"
        >
          <option value="">All stock levels</option>
          <option value="LOW">Low stock (at or below reorder level)</option>
          <option value="OUT">Out of stock</option>
          <option value="IN_STOCK">In stock</option>
        </select>
        <div className="sm:w-52">
          <FilterSelect label="Category" placeholder="All categories" value={category} onChange={resetPage(setCategory)} load={loadCategoryOptions} />
        </div>
        <div className="sm:w-52">
          <FilterSelect label="Publisher" placeholder="All publishers" value={publisher} onChange={resetPage(setPublisher)} load={loadPublisherOptions} />
        </div>
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
                <SortableHeader label="Title" field="title" sort={sort} onSort={onSort} />
                <SortableHeader label="ISBN" field="isbn" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 font-medium">Category</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Publisher</th>
                <SortableHeader label="Stock" field="quantity" sort={sort} onSort={onSort} />
                <SortableHeader label="Reorder at" field="reorderLevel" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Selling price</th>
                <th scope="col" className="px-4 py-2.5">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((i) => (
                <tr key={i.productId} className="hover:bg-slate-50">
                  <td className="px-4 py-2.5">
                    <div className="font-medium text-slate-900">{i.title}</div>
                    {i.author && <div className="text-xs text-slate-500">{i.author}</div>}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5 font-mono text-xs">{i.isbn ?? "-"}</td>
                  <td className="px-4 py-2.5">{i.category?.name ?? "-"}</td>
                  <td className="px-4 py-2.5">{i.publisher?.name ?? "-"}</td>
                  <td className="whitespace-nowrap px-4 py-2.5">
                    <span className="mr-2 text-base font-semibold tabular-nums text-slate-900">{i.quantity}</span>
                    {i.stockLevel !== "OK" && <Badge tone={LEVEL_TONE[i.stockLevel]}>{STOCK_LEVEL_LABELS[i.stockLevel]}</Badge>}
                  </td>
                  <td className="px-4 py-2.5 tabular-nums">{i.reorderLevel}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{formatCurrency(i.sellingPrice)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right">
                    <Link
                      href={`/inventory/movements?productId=${i.productId}`}
                      className="mr-1 inline-flex h-8 items-center rounded-md px-3 text-xs font-medium text-slate-600 hover:bg-slate-100"
                    >
                      History
                    </Link>
                    {canAdjust && (
                      <>
                        <Button size="sm" variant="ghost" onClick={() => setReordering(i)}>
                          Reorder level
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setAdjusting(i)}>
                          Adjust
                        </Button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {list.loading && !list.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading inventory..." />
          </div>
        )}
        {!list.loading && list.data && rows.length === 0 && (
          <EmptyState
            title="No products match"
            description={search || status || category || publisher ? "Try clearing some filters." : "Add products under Master Data."}
          />
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

      {adjusting && (
        <Modal open title={`Adjust stock: ${adjusting.title}`} onClose={() => setAdjusting(null)}>
          <AdjustmentForm
            key={adjusting.productId}
            initialProduct={{ id: adjusting.productId, label: adjusting.title }}
            lockProduct
            onCancel={() => setAdjusting(null)}
            onDone={(a) => {
              setAdjusting(null);
              toast.success(
                `Stock of "${a.product.title}" adjusted ${formatSignedQuantity(a.adjustmentQuantity)} (now ${a.resultingQuantity})`,
              );
              refresh();
            }}
          />
        </Modal>
      )}
      {reordering && (
        <ReorderLevelDialog
          key={reordering.productId}
          item={reordering}
          onClose={() => setReordering(null)}
          onSaved={(saved) => {
            setReordering(null);
            toast.success(`Reorder level of "${saved.title}" set to ${saved.reorderLevel}`);
            refresh();
          }}
        />
      )}
    </>
  );
}

function SummaryCard({
  label,
  value,
  tone,
  onClick,
}: {
  label: string;
  value: number | undefined;
  tone?: "amber" | "red";
  onClick?: () => void;
}) {
  const color = tone === "red" ? "text-red-600" : tone === "amber" ? "text-amber-600" : "text-slate-900";
  const content = (
    <>
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${color}`}>{value === undefined ? "–" : value.toLocaleString("en-PK")}</p>
    </>
  );
  const base = "rounded-lg border border-slate-200 bg-white px-4 py-3 text-left";
  return onClick ? (
    <button type="button" onClick={onClick} className={`${base} hover:border-slate-300 hover:bg-slate-50`}>
      {content}
    </button>
  ) : (
    <div className={base}>{content}</div>
  );
}

function ReorderLevelDialog({
  item,
  onClose,
  onSaved,
}: {
  item: InventoryItem;
  onClose: () => void;
  onSaved: (item: InventoryItem) => void;
}) {
  const [value, setValue] = useState(String(item.reorderLevel));
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const n = Number(value);
    if (value.trim() === "" || !Number.isInteger(n) || n < 0) {
      setError("Enter a whole number, 0 or more");
      return;
    }
    setSaving(true);
    try {
      onSaved(await inventoryService.setReorderLevel(item.productId, n));
    } catch (err) {
      setError(err instanceof ApiError ? (err.fieldErrors.reorderLevel ?? err.message) : "Could not save");
      setSaving(false);
    }
  }

  return (
    <FormDialog title={`Reorder level: ${item.title}`} submitLabel="Save" saving={saving} formError={null} onSubmit={submit} onClose={onClose}>
      <p className="text-sm text-slate-600">
        The product is flagged as low stock when its quantity is at or below this number. Current stock: <strong>{item.quantity}</strong>.
      </p>
      <TextField
        label="Reorder level"
        type="number"
        inputMode="numeric"
        min="0"
        step="1"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        error={error ?? undefined}
        autoFocus
        required
      />
    </FormDialog>
  );
}

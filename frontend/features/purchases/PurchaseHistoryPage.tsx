"use client";

import Link from "next/link";
import { useState } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SearchInput, SelectField, TextField } from "@/components/ui/FormField";
import { Pagination } from "@/components/ui/Pagination";
import { SortableHeader, nextSort, toSortParam, type SortState } from "@/components/ui/SortableHeader";
import { Spinner } from "@/components/ui/Spinner";
import { useAuth } from "@/features/auth/AuthProvider";
import { DocumentStatusBadge, PaymentStatusBadge } from "@/features/documents/StatusBadges";
import { loadStockedProductOptions } from "@/features/inventory/lookups";
import { loadSupplierOptions } from "@/features/master/lookups";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { purchaseService } from "@/services/purchaseService";
import type { DocumentStatus, PaymentStatus } from "@/types/purchase";
import { formatCurrency, formatDate } from "@/utils/format";
import { PAYMENT_STATUS_LABELS } from "@/utils/labels";

const PAGE_SIZE = 25;

export function PurchaseHistoryPage() {
  const { hasPermission } = useAuth();
  const [search, setSearch] = useState("");
  const [supplier, setSupplier] = useState<Option | null>(null);
  const [product, setProduct] = useState<Option | null>(null);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [payment, setPayment] = useState<PaymentStatus | "">("");
  const [status, setStatus] = useState<DocumentStatus | "">("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>({ field: "purchaseDate", direction: "desc" });
  const debouncedSearch = useDebouncedValue(search);

  const dateError = from && to && to < from ? "'To' must be on or after 'From'" : undefined;
  const list = useApiQuery(
    (signal) =>
      dateError
        ? Promise.resolve(null)
        : purchaseService.list(
            {
              search: debouncedSearch,
              supplierId: supplier?.id,
              productId: product?.id,
              from: from || undefined,
              to: to || undefined,
              paymentStatus: payment || undefined,
              status: status || undefined,
              page,
              size: PAGE_SIZE,
              sort: toSortParam(sort),
            },
            signal,
          ),
    [debouncedSearch, supplier?.id ?? null, product?.id ?? null, from, to, payment, status, page, sort, dateError ?? null],
  );

  const reset = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(0);
  };
  const onSort = (field: string) => {
    setSort((s) => nextSort(s, field));
    setPage(0);
  };
  const rows = list.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Purchase History"
        description="All purchases, newest first."
        actions={
          hasPermission("PURCHASE_CREATE") ? (
            <Link href="/purchases/new" className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
              New purchase
            </Link>
          ) : undefined
        }
      />

      <div className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="space-y-1">
          <label className="block text-sm font-medium text-slate-700" htmlFor="purchase-search">
            Search
          </label>
          <SearchInput
            id="purchase-search"
            placeholder="Number, supplier or supplier invoice"
            value={search}
            onChange={(e) => reset(setSearch)(e.target.value)}
          />
        </div>
        <AsyncSelect label="Supplier" value={supplier} onChange={reset(setSupplier)} load={loadSupplierOptions} placeholder="All suppliers" />
        <AsyncSelect label="Product" value={product} onChange={reset(setProduct)} load={loadStockedProductOptions} placeholder="All products" />
        <div className="grid grid-cols-2 gap-2">
          <SelectField label="Payment" value={payment} onChange={(e) => reset(setPayment)(e.target.value as PaymentStatus | "")}>
            <option value="">All</option>
            {Object.entries(PAYMENT_STATUS_LABELS).map(([k, label]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </SelectField>
          <SelectField label="Status" value={status} onChange={(e) => reset(setStatus)(e.target.value as DocumentStatus | "")}>
            <option value="">All</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </SelectField>
        </div>
        <TextField label="From" type="date" value={from} onChange={(e) => reset(setFrom)(e.target.value)} />
        <TextField label="To" type="date" value={to} onChange={(e) => reset(setTo)(e.target.value)} error={dateError} />
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
                <SortableHeader label="Number" field="purchaseNumber" sort={sort} onSort={onSort} />
                <SortableHeader label="Date" field="purchaseDate" sort={sort} onSort={onSort} />
                <SortableHeader label="Supplier" field="supplier" sort={sort} onSort={onSort} />
                <SortableHeader label="Total" field="total" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 font-medium">Payment</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Created by</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((p) => (
                <tr key={p.id} className={`hover:bg-slate-50 ${p.status === "CANCELLED" ? "text-slate-400" : ""}`}>
                  <td className="whitespace-nowrap px-4 py-2.5">
                    <Link href={`/purchases/${p.id}`} className="font-mono text-xs font-medium text-blue-700 hover:underline">
                      {p.purchaseNumber}
                    </Link>{" "}
                    <DocumentStatusBadge status={p.status} />
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5">{formatDate(p.purchaseDate)}</td>
                  <td className="px-4 py-2.5">
                    <div>{p.supplier.name}</div>
                    {p.supplierInvoiceNumber && <div className="text-xs text-slate-500">Their ref: {p.supplierInvoiceNumber}</div>}
                  </td>
                  <td className={`whitespace-nowrap px-4 py-2.5 text-right tabular-nums ${p.status === "CANCELLED" ? "line-through" : ""}`}>
                    {formatCurrency(p.total)}
                  </td>
                  <td className="px-4 py-2.5">
                    <PaymentStatusBadge status={p.paymentStatus} />
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5">{p.createdBy?.name ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {list.loading && !list.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading purchases..." />
          </div>
        )}
        {!list.loading && list.data && rows.length === 0 && <EmptyState title="No purchases found" />}
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
    </>
  );
}

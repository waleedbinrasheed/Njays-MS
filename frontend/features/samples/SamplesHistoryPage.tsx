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
import { loadStockedProductOptions } from "@/features/inventory/lookups";
import { loadAnyRepresentativeOptions, loadCustomerOptions } from "@/features/master/lookups";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { sampleService } from "@/services/sampleService";
import type { SampleStatus } from "@/types/sample";
import { formatCurrency, formatDate } from "@/utils/format";
import { SAMPLE_STATUS_LABELS } from "@/utils/labels";
import { SampleStatusBadge } from "./SampleStatusBadge";

const PAGE_SIZE = 25;

export function SamplesHistoryPage() {
  const { hasPermission } = useAuth();
  const [search, setSearch] = useState("");
  const [representative, setRepresentative] = useState<Option | null>(null);
  const [customer, setCustomer] = useState<Option | null>(null);
  const [product, setProduct] = useState<Option | null>(null);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [status, setStatus] = useState<SampleStatus | "">("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>({ field: "issueDate", direction: "desc" });
  const debouncedSearch = useDebouncedValue(search);

  const dateError = from && to && to < from ? "'To' must be on or after 'From'" : undefined;
  const list = useApiQuery(
    (signal) =>
      dateError
        ? Promise.resolve(null)
        : sampleService.list(
            {
              search: debouncedSearch,
              representativeId: representative?.id,
              customerId: customer?.id,
              productId: product?.id,
              from: from || undefined,
              to: to || undefined,
              status: status || undefined,
              page,
              size: PAGE_SIZE,
              sort: toSortParam(sort),
            },
            signal,
          ),
    [debouncedSearch, representative?.id ?? null, customer?.id ?? null, product?.id ?? null, from, to, status, page, sort, dateError ?? null],
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
        title="Sample Issues"
        description="Books given to representatives as samples (SAMPLE SALE, not retail). Outstanding = issued − returned."
        actions={
          hasPermission("SAMPLE_MANAGE") ? (
            <Link href="/samples/new" className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
              New sample issue
            </Link>
          ) : undefined
        }
      />

      <div className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-3">
        <div className="space-y-1">
          <label className="block text-sm font-medium text-slate-700" htmlFor="sample-search">
            Search
          </label>
          <SearchInput id="sample-search" placeholder="Sample no., representative or customer" value={search} onChange={(e) => reset(setSearch)(e.target.value)} />
        </div>
        <AsyncSelect label="Representative" value={representative} onChange={reset(setRepresentative)} load={loadAnyRepresentativeOptions} placeholder="All representatives" />
        <AsyncSelect label="Customer" value={customer} onChange={reset(setCustomer)} load={loadCustomerOptions} placeholder="All customers" />
        <AsyncSelect label="Book" value={product} onChange={reset(setProduct)} load={loadStockedProductOptions} placeholder="All books" />
        <TextField label="From" type="date" value={from} onChange={(e) => reset(setFrom)(e.target.value)} />
        <TextField label="To" type="date" value={to} onChange={(e) => reset(setTo)(e.target.value)} error={dateError} />
        <SelectField label="Status" value={status} onChange={(e) => reset(setStatus)(e.target.value as SampleStatus | "")}>
          <option value="">All</option>
          {Object.entries(SAMPLE_STATUS_LABELS).map(([k, label]) => (
            <option key={k} value={k}>
              {label}
            </option>
          ))}
        </SelectField>
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
                <SortableHeader label="Sample" field="sampleNumber" sort={sort} onSort={onSort} />
                <SortableHeader label="Date" field="issueDate" sort={sort} onSort={onSort} />
                <SortableHeader label="Representative" field="representative" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 font-medium">Customer</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Issued qty</th>
                <SortableHeader label="Issued amount" field="total" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Returned</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Outstanding</th>
                <SortableHeader label="Status" field="status" sort={sort} onSort={onSort} />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((s) => {
                const cancelled = s.status === "CANCELLED";
                return (
                  <tr key={s.id} className={`hover:bg-slate-50 ${cancelled ? "text-slate-400" : ""}`}>
                    <td className="whitespace-nowrap px-4 py-2.5">
                      <Link href={`/samples/${s.id}`} className="font-mono text-xs font-medium text-blue-700 hover:underline">
                        {s.sampleNumber}
                      </Link>
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5">{formatDate(s.issueDate)}</td>
                    <td className="px-4 py-2.5">
                      <div>{s.representative.name}</div>
                      {s.representative.area && <div className="text-xs text-slate-500">{s.representative.area}</div>}
                    </td>
                    <td className="px-4 py-2.5">{s.customer?.name ?? "-"}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{s.totalQuantity}</td>
                    <td className={`whitespace-nowrap px-4 py-2.5 text-right tabular-nums ${cancelled ? "line-through" : ""}`}>
                      {formatCurrency(s.total)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                      {s.returnedQuantity ? (
                        <>
                          {s.returnedQuantity} · {formatCurrency(s.returnedTotal)}
                        </>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-right font-medium tabular-nums">
                      {s.outstandingQuantity ? (
                        <>
                          {s.outstandingQuantity} · {formatCurrency(s.outstandingTotal)}
                        </>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td className="px-4 py-2.5">
                      <SampleStatusBadge status={s.status} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {list.loading && !list.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading samples..." />
          </div>
        )}
        {!list.loading && list.data && rows.length === 0 && <EmptyState title="No samples found" />}
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

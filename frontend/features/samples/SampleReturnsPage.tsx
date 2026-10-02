"use client";

import Link from "next/link";
import { useState } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SearchInput, TextField } from "@/components/ui/FormField";
import { Pagination } from "@/components/ui/Pagination";
import { SortableHeader, nextSort, toSortParam, type SortState } from "@/components/ui/SortableHeader";
import { Spinner } from "@/components/ui/Spinner";
import { loadAnyRepresentativeOptions } from "@/features/master/lookups";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { sampleService } from "@/services/sampleService";
import { formatCurrency, formatDate } from "@/utils/format";

const PAGE_SIZE = 25;

export function SampleReturnsPage() {
  const [search, setSearch] = useState("");
  const [representative, setRepresentative] = useState<Option | null>(null);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>({ field: "returnDate", direction: "desc" });
  const debouncedSearch = useDebouncedValue(search);

  const dateError = from && to && to < from ? "'To' must be on or after 'From'" : undefined;
  const list = useApiQuery(
    (signal) =>
      dateError
        ? Promise.resolve(null)
        : sampleService.listReturns(
            {
              search: debouncedSearch,
              representativeId: representative?.id,
              from: from || undefined,
              to: to || undefined,
              page,
              size: PAGE_SIZE,
              sort: toSortParam(sort),
            },
            signal,
          ),
    [debouncedSearch, representative?.id ?? null, from, to, page, sort, dateError ?? null],
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
        title="Sample Returns"
        description="Samples brought back by representatives. To record one, open the sample and choose Return samples."
      />

      <div className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="space-y-1">
          <label className="block text-sm font-medium text-slate-700" htmlFor="sample-return-search">
            Search
          </label>
          <SearchInput
            id="sample-return-search"
            placeholder="Return no., sample no. or representative"
            value={search}
            onChange={(e) => reset(setSearch)(e.target.value)}
          />
        </div>
        <AsyncSelect label="Representative" value={representative} onChange={reset(setRepresentative)} load={loadAnyRepresentativeOptions} placeholder="All representatives" />
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
                <SortableHeader label="Return" field="returnNumber" sort={sort} onSort={onSort} />
                <SortableHeader label="Date" field="returnDate" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 font-medium">Sample</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Representative</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Units</th>
                <SortableHeader label="Amount" field="total" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 font-medium">Recorded by</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((r) => (
                <tr key={r.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-4 py-2.5">
                    <Link href={`/samples/returns/${r.id}`} className="font-mono text-xs font-medium text-blue-700 hover:underline">
                      {r.returnNumber}
                    </Link>
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5">{formatDate(r.returnDate)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5">
                    <Link href={`/samples/${r.sampleId}`} className="font-mono text-xs text-blue-700 hover:underline">
                      {r.sampleNumber}
                    </Link>
                  </td>
                  <td className="px-4 py-2.5">{r.representative.name}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums">{r.totalQuantity}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{formatCurrency(r.total)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5">{r.createdBy?.name ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {list.loading && !list.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading sample returns..." />
          </div>
        )}
        {!list.loading && list.data && rows.length === 0 && <EmptyState title="No sample returns found" />}
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

"use client";

import Link from "next/link";
import { useState } from "react";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SearchInput, TextField } from "@/components/ui/FormField";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { returnServices } from "@/services/returnService";
import type { ReturnKind } from "@/types/returns";
import { formatCurrency, formatDate } from "@/utils/format";
import { RETURN_KINDS } from "./returnKinds";

export function ReturnHistoryPage({ kind }: { kind: ReturnKind }) {
  const cfg = RETURN_KINDS[kind];
  const [search, setSearch] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search);
  const dateError = from && to && to < from ? "'To' must be on or after 'From'" : undefined;

  const list = useApiQuery(
    (signal) =>
      dateError
        ? Promise.resolve(null)
        : returnServices[kind].list({ search: debouncedSearch, from: from || undefined, to: to || undefined, page, size: 25 }, signal),
    [kind, debouncedSearch, from, to, page, dateError ?? null],
  );
  const rows = list.data?.content ?? [];

  return (
    <>
      <PageHeader
        title={cfg.title}
        description={`To record a return, open the ${cfg.documentLabel.toLowerCase()} and choose "Return items".`}
      />
      <div className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-3">
        <div className="space-y-1">
          <label htmlFor="return-search" className="block text-sm font-medium text-slate-700">
            Search
          </label>
          <SearchInput
            id="return-search"
            placeholder={`Return no., ${cfg.documentLabel.toLowerCase()} no. or ${cfg.partyLabel.toLowerCase()}`}
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <TextField label="From" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        <TextField label="To" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} error={dateError} />
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
                <th className="px-4 py-2.5 font-medium">Return</th>
                <th className="px-4 py-2.5 font-medium">Date</th>
                <th className="px-4 py-2.5 font-medium">{cfg.documentLabel}</th>
                <th className="px-4 py-2.5 font-medium">{cfg.partyLabel}</th>
                <th className="px-4 py-2.5 text-right font-medium">{cfg.amountLabel}</th>
                <th className="px-4 py-2.5 font-medium">Recorded by</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((r) => (
                <tr key={r.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-4 py-2.5">
                    <Link href={cfg.detailHref(r.id)} className="font-mono text-xs font-medium text-blue-700 hover:underline">
                      {r.returnNumber}
                    </Link>
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5">{formatDate(r.returnDate)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5">
                    <Link href={cfg.documentHref(r.documentId)} className="font-mono text-xs text-slate-700 hover:underline">
                      {r.documentNumber}
                    </Link>
                  </td>
                  <td className="px-4 py-2.5">{r.party.name}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{formatCurrency(r.total)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5">{r.createdBy?.name ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {list.loading && !list.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading returns..." />
          </div>
        )}
        {!list.loading && list.data && rows.length === 0 && <EmptyState title="No returns found" />}
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

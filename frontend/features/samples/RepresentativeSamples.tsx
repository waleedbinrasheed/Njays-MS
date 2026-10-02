"use client";

import Link from "next/link";
import { useState } from "react";
import { EmptyState, ErrorBanner } from "@/components/ui/Feedback";
import { SelectField } from "@/components/ui/FormField";
import { Pagination } from "@/components/ui/Pagination";
import { SortableHeader } from "@/components/ui/SortableHeader";
import { Spinner } from "@/components/ui/Spinner";
import { useAuth } from "@/features/auth/AuthProvider";
import { useReport } from "@/features/reports/useReport";
import { sampleService } from "@/services/sampleService";
import type { SampleStatus } from "@/types/sample";
import { formatCurrency } from "@/utils/format";
import { SAMPLE_STATUS_LABELS } from "@/utils/labels";
import { sampleLineColumns } from "./SampleLinesTable";

/** Sample history on a representative's page: every book issued to them and what is still out. */
export function RepresentativeSamples({ representativeId }: { representativeId: number }) {
  const { hasPermission } = useAuth();
  const [status, setStatus] = useState<SampleStatus | "">("");
  const r = useReport(
    sampleService.reportLines,
    { representativeId, status: status || undefined },
    { field: "date", direction: "desc" },
  );
  const columns = sampleLineColumns(false);
  const rows = r.data?.content ?? [];
  const s = r.data?.summary;

  return (
    <section className="mt-5 rounded-lg border border-slate-200 bg-white">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
        <h2 className="text-sm font-semibold text-slate-900">Samples</h2>
        <div className="flex items-center gap-3">
          {r.loading && r.data && <Spinner size="sm" />}
          <div className="w-48">
            <SelectField label="Status" value={status} onChange={(e) => setStatus(e.target.value as SampleStatus | "")}>
              <option value="">All</option>
              {Object.entries(SAMPLE_STATUS_LABELS).map(([k, label]) => (
                <option key={k} value={k}>
                  {label}
                </option>
              ))}
            </SelectField>
          </div>
          {hasPermission("SAMPLE_MANAGE") && (
            <Link href="/samples/new" className="text-sm text-blue-700 hover:underline">
              New sample issue
            </Link>
          )}
          <Link href="/reports/samples" className="text-sm text-blue-700 hover:underline">
            Sample report
          </Link>
        </div>
      </header>

      {s && (
        <dl className="grid gap-3 border-b border-slate-200 px-4 py-3 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-slate-500">Issued</dt>
            <dd className="font-medium tabular-nums">
              {s.issuedQuantity} · {formatCurrency(s.issuedAmount)}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Returned</dt>
            <dd className="font-medium tabular-nums">
              {s.returnedQuantity} · {formatCurrency(s.returnedAmount)}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Outstanding</dt>
            <dd className={`font-semibold tabular-nums ${s.outstandingQuantity > 0 ? "text-amber-700" : ""}`}>
              {s.outstandingQuantity} · {formatCurrency(s.outstandingAmount)}
            </dd>
          </div>
        </dl>
      )}

      {r.error && (
        <div className="p-4">
          <ErrorBanner message={r.error} onRetry={r.reload} />
        </div>
      )}
      {r.data && rows.length === 0 ? (
        <EmptyState title="No samples" description={status ? "Try another status." : "Nothing has been issued to this representative yet."} />
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                {columns.map((c) =>
                  c.sortField ? (
                    <SortableHeader key={c.header} label={c.header} field={c.sortField} sort={r.sort} onSort={r.onSort} />
                  ) : (
                    <th key={c.header} scope="col" className={`px-4 py-2.5 font-medium ${c.align === "right" ? "text-right" : ""}`}>
                      {c.header}
                    </th>
                  ),
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((row) => (
                <tr key={row.itemId} className="hover:bg-slate-50">
                  {columns.map((c) => (
                    <td key={c.header} className={`whitespace-nowrap px-4 py-2 ${c.align === "right" ? "text-right tabular-nums" : ""}`}>
                      {c.render(row)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {r.loading && !r.data && (
        <div className="flex justify-center py-8 text-blue-600">
          <Spinner label="Loading samples..." />
        </div>
      )}
      {r.data && <Pagination {...r.data} onPageChange={r.setPage} />}
    </section>
  );
}

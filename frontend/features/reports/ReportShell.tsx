"use client";

import type { ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { Pagination } from "@/components/ui/Pagination";
import { SortableHeader, type SortState } from "@/components/ui/SortableHeader";
import { Spinner } from "@/components/ui/Spinner";
import { formatDateTime } from "@/utils/format";

export interface ReportColumn<T> {
  header: string;
  render: (row: T) => ReactNode;
  sortField?: string;
  align?: "right";
  className?: string;
}

interface ReportShellProps<T> {
  title: string;
  description: string;
  filters: ReactNode;
  /** Plain-text description of active filters, printed at the top of the report. */
  filterSummary: string;
  summary?: ReactNode;
  columns: ReportColumn<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => number;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  sort: SortState;
  onSort: (field: string) => void;
  page?: { page: number; size: number; totalElements: number; totalPages: number };
  onPageChange: (page: number) => void;
  pageSize: number;
  onPageSizeChange: (size: number) => void;
}

/** Shared frame for all reports: filters, totals, sortable table, paging, print. */
export function ReportShell<T>({
  title,
  description,
  filters,
  filterSummary,
  summary,
  columns,
  rows,
  rowKey,
  loading,
  error,
  onRetry,
  sort,
  onSort,
  page,
  onPageChange,
  pageSize,
  onPageSizeChange,
}: ReportShellProps<T>) {
  return (
    <>
      <div className="print:hidden">
        <PageHeader
          title={title}
          description={description}
          actions={
            <>
              <label className="flex items-center gap-2 text-sm text-slate-600">
                Rows
                <select
                  value={pageSize}
                  onChange={(e) => onPageSizeChange(Number(e.target.value))}
                  className="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
                >
                  {[25, 50, 100, 200].map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
              <Button variant="secondary" onClick={() => window.print()}>
                Print / Save as PDF
              </Button>
            </>
          }
        />
        <div className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-4">{filters}</div>
      </div>

      <div className="mb-4 hidden print:block">
        <h1 className="text-lg font-semibold">{title}</h1>
        <p className="text-xs text-slate-600">
          {filterSummary || "No filters"} · Printed {formatDateTime(new Date().toISOString())}
          {page && page.totalPages > 1 ? ` · Page ${page.page + 1} of ${page.totalPages}` : ""}
        </p>
      </div>

      {error && (
        <div className="mb-4">
          <ErrorBanner message={error} onRetry={onRetry} />
        </div>
      )}
      {summary && <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4 print:grid-cols-4">{summary}</div>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white print:border-0">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500 print:bg-white">
              <tr>
                {columns.map((c) =>
                  c.sortField ? (
                    <SortableHeader key={c.header} label={c.header} field={c.sortField} sort={sort} onSort={onSort} />
                  ) : (
                    <th key={c.header} scope="col" className={`px-4 py-2.5 font-medium ${c.align === "right" ? "text-right" : ""}`}>
                      {c.header}
                    </th>
                  ),
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {(rows ?? []).map((row) => (
                <tr key={rowKey(row)} className="align-top hover:bg-slate-50">
                  {columns.map((c) => (
                    <td
                      key={c.header}
                      className={`px-4 py-2 ${c.align === "right" ? "whitespace-nowrap text-right tabular-nums" : ""} ${c.className ?? ""}`}
                    >
                      {c.render(row)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {loading && !rows && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Running report..." />
          </div>
        )}
        {!loading && rows && rows.length === 0 && <EmptyState title="No rows match these filters" />}
        {page && (
          <div className="print:hidden">
            <Pagination {...page} onPageChange={onPageChange} />
          </div>
        )}
      </div>
    </>
  );
}

"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/Feedback";
import { SearchInput, SelectField } from "@/components/ui/FormField";
import { StatTile } from "@/components/ui/StatTile";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { reportService } from "@/services/reportService";
import type { FabricStockRow } from "@/types/report";
import { formatCurrency } from "@/utils/format";
import { ReportShell, type ReportColumn } from "./ReportShell";
import { describeFilters, useReport } from "./useReport";

/** Fabric on hand with cost value (metres x cost per metre). */
export function FabricStockReportPage() {
  const [search, setSearch] = useState("");
  const [stock, setStock] = useState<"" | "LOW">("");
  const debounced = useDebouncedValue(search);

  const r = useReport(
    (q, signal) =>
      reportService.fabricStock(
        { search: q.search as string | undefined, lowStockOnly: q.lowStock as boolean | undefined, page: q.page, size: q.size, sort: q.sort },
        signal,
      ),
    { search: debounced || undefined, lowStock: stock === "LOW" || undefined },
    { field: "code", direction: "asc" },
  );

  const columns: ReportColumn<FabricStockRow>[] = [
    { header: "Code", sortField: "code", render: (f) => <span className="font-mono text-xs">{f.code}</span> },
    { header: "Fabric", sortField: "name", render: (f) => <>{f.name}{f.colour ? <span className="text-slate-500"> · {f.colour}</span> : null}</> },
    {
      header: "On hand (m)",
      sortField: "quantityOnHand",
      align: "right",
      render: (f) => (
        <>
          {f.quantityOnHand.toFixed(3)}
          {f.lowStock && (
            <span className="ml-1">
              <Badge tone="amber">Low</Badge>
            </span>
          )}
        </>
      ),
    },
    { header: "Reorder at (m)", sortField: "reorderLevel", align: "right", render: (f) => f.reorderLevel.toFixed(3) },
    { header: "Cost / m", sortField: "costPerMetre", align: "right", render: (f) => formatCurrency(f.costPerMetre) },
    { header: "Cost value", sortField: "costValue", align: "right", render: (f) => formatCurrency(f.costValue) },
  ];

  return (
    <ReportShell
      title="Fabric Stock Report"
      description="Fabric on hand with its cost value (metres × cost per metre)."
      filters={
        <>
          <div className="space-y-1">
            <label htmlFor="fabric-report-search" className="block text-sm font-medium text-slate-700">
              Fabric
            </label>
            <SearchInput id="fabric-report-search" placeholder="Code, name or colour" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <SelectField label="Stock" value={stock} onChange={(e) => setStock(e.target.value as "" | "LOW")}>
            <option value="">All fabrics</option>
            <option value="LOW">Low stock only</option>
          </SelectField>
        </>
      }
      filterSummary={describeFilters([
        ["Search", debounced],
        ["Stock", stock ? "Low stock" : null],
      ])}
      summary={r.data && <StatTile label="Cost value" value={formatCurrency(r.data.totals.costValue)} />}
      columns={columns}
      rows={r.data?.content}
      rowKey={(f) => f.productId}
      loading={r.loading}
      error={r.error}
      onRetry={r.reload}
      sort={r.sort}
      onSort={r.onSort}
      page={r.data ? { page: r.data.page, size: r.data.size, totalElements: r.data.totalElements, totalPages: r.data.totalPages } : undefined}
      onPageChange={r.setPage}
      pageSize={r.size}
      onPageSizeChange={r.setSize}
    />
  );
}

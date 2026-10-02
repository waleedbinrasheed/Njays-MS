"use client";

import Link from "next/link";
import type { ReportColumn } from "@/features/reports/ReportShell";
import type { SampleLineRow } from "@/types/sample";
import { formatCurrency, formatDate } from "@/utils/format";
import { SampleStatusBadge } from "./SampleStatusBadge";

/**
 * Columns of the representative sample history / Sample report detail view:
 * one row per issued book, with issued, returned and outstanding quantity and amount.
 */
export function sampleLineColumns(showRepresentative: boolean): ReportColumn<SampleLineRow>[] {
  const muted = (row: SampleLineRow) => (row.status === "CANCELLED" ? "text-slate-400" : "");
  return [
    {
      header: "Sample",
      sortField: "number",
      render: (row) => (
        <Link href={`/samples/${row.sampleId}`} className="font-mono text-xs font-medium text-blue-700 hover:underline print:text-slate-900">
          {row.sampleNumber}
        </Link>
      ),
    },
    { header: "Date", sortField: "date", render: (row) => formatDate(row.issueDate) },
    ...(showRepresentative
      ? [{ header: "Representative", sortField: "representative", render: (row: SampleLineRow) => row.representative }]
      : []),
    {
      header: "Book",
      sortField: "product",
      render: (row) => (
        <>
          <div className={muted(row)}>{row.title}</div>
          {row.isbn && <div className="font-mono text-xs text-slate-500">{row.isbn}</div>}
        </>
      ),
    },
    { header: "Issued qty", sortField: "issuedQuantity", align: "right", render: (row) => row.issuedQuantity },
    { header: "Sample price", align: "right", render: (row) => formatCurrency(row.unitPrice) },
    {
      header: "Issued amount",
      sortField: "issuedAmount",
      align: "right",
      render: (row) => <span className={row.status === "CANCELLED" ? "line-through" : ""}>{formatCurrency(row.issuedAmount)}</span>,
    },
    { header: "Returned qty", align: "right", render: (row) => row.returnedQuantity || "-" },
    { header: "Returned amount", align: "right", render: (row) => (Number(row.returnedAmount) ? formatCurrency(row.returnedAmount) : "-") },
    {
      header: "Outstanding qty",
      sortField: "outstandingQuantity",
      align: "right",
      render: (row) => <strong>{row.outstandingQuantity}</strong>,
    },
    {
      header: "Outstanding amount",
      sortField: "outstandingAmount",
      align: "right",
      render: (row) => <strong>{formatCurrency(row.outstandingAmount)}</strong>,
    },
    { header: "Status", sortField: "status", render: (row) => <SampleStatusBadge status={row.status} /> },
  ];
}

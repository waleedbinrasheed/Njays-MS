"use client";

import Link from "next/link";
import { useState } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { SelectField, TextField } from "@/components/ui/FormField";
import { StatTile } from "@/components/ui/StatTile";
import { OrderStatusBadge, PaymentStatusBadge } from "@/features/orders/OrderBadges";
import { loadCustomerOptions } from "@/features/master/lookups";
import { reportService, type OrderReportQuery } from "@/services/reportService";
import type { OrderStatus } from "@/types/order";
import { formatCurrency, formatDate } from "@/utils/format";
import { ORDER_STATUSES, ORDER_STATUS_LABELS } from "@/utils/labels";
import { ReportShell, type ReportColumn } from "./ReportShell";
import { describeFilters, useReport } from "./useReport";
import type { OrderReportRow } from "@/types/report";

const rangeError = (from: string, to: string) => (from && to && to < from ? "'To' must be on or after 'From'" : undefined);

/** Orders by order date. Cancelled orders are listed but are not counted in the totals. */
export function OrdersReportPage() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [status, setStatus] = useState<OrderStatus | "">("");
  const [customer, setCustomer] = useState<Option | null>(null);

  const r = useReport(
    (q, signal) => reportService.orders(q as unknown as OrderReportQuery, signal),
    {
      from: from || undefined,
      to: to || undefined,
      status: status || undefined,
      customerId: customer?.id,
    },
    { field: "orderDate", direction: "desc" },
    !!rangeError(from, to),
  );

  const columns: ReportColumn<OrderReportRow>[] = [
    { header: "Order no", sortField: "orderNumber", render: (o) => <Link href={`/orders/${o.id}`} className="font-medium text-blue-700 hover:underline">{o.orderNumber}</Link> },
    { header: "Order date", sortField: "orderDate", render: (o) => formatDate(o.orderDate) },
    { header: "Customer", sortField: "customerName", render: (o) => o.customerName },
    { header: "Due date", sortField: "dueDate", render: (o) => formatDate(o.dueDate) },
    { header: "Status", render: (o) => <OrderStatusBadge status={o.status} /> },
    { header: "Total", sortField: "total", align: "right", render: (o) => formatCurrency(o.total) },
    { header: "Paid", align: "right", render: (o) => formatCurrency(o.paid) },
    { header: "Balance", sortField: "balance", align: "right", render: (o) => formatCurrency(o.balance) },
    { header: "Payment", render: (o) => <PaymentStatusBadge status={o.paymentStatus} /> },
  ];

  const totals = r.data?.totals;

  return (
    <ReportShell
      title="Orders Report"
      description="Orders by order date, with totals. Cancelled orders are listed but not counted."
      filters={
        <>
          <TextField label="Order date from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <TextField label="Order date to" type="date" value={to} onChange={(e) => setTo(e.target.value)} error={rangeError(from, to)} />
          <SelectField label="Status" value={status} onChange={(e) => setStatus(e.target.value as OrderStatus | "")}>
            <option value="">All statuses</option>
            {ORDER_STATUSES.map((s) => (
              <option key={s} value={s}>
                {ORDER_STATUS_LABELS[s]}
              </option>
            ))}
          </SelectField>
          <AsyncSelect label="Customer" value={customer} onChange={setCustomer} load={loadCustomerOptions} placeholder="All customers" />
        </>
      }
      filterSummary={describeFilters([
        ["From", from],
        ["To", to],
        ["Status", status ? ORDER_STATUS_LABELS[status] : null],
        ["Customer", customer?.label],
      ])}
      summary={
        totals && (
          <>
            <StatTile label="Orders" value={totals.orders.toLocaleString("en-PK")} />
            <StatTile label="Total" value={formatCurrency(totals.total)} />
            <StatTile label="Paid" value={formatCurrency(totals.paid)} />
            <StatTile label="Balance" value={formatCurrency(totals.balance)} />
          </>
        )
      }
      columns={columns}
      rows={r.data?.content}
      rowKey={(o) => o.id}
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

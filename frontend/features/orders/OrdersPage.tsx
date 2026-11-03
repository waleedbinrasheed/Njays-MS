"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SearchInput, TextField } from "@/components/ui/FormField";
import { Pagination } from "@/components/ui/Pagination";
import { SortableHeader, nextSort, toSortParam, type SortState } from "@/components/ui/SortableHeader";
import { Spinner } from "@/components/ui/Spinner";
import { useAuth } from "@/features/auth/AuthProvider";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { orderService } from "@/services/orderService";
import type { OrderStatus } from "@/types/order";
import { formatCurrency, formatDate } from "@/utils/format";
import { ORDER_STATUSES, ORDER_STATUS_LABELS } from "@/utils/labels";
import { OrderStatusBadge, PaymentStatusBadge } from "./OrderBadges";

const PAGE_SIZE = 20;

export function OrdersPage() {
  const router = useRouter();
  const { hasPermission } = useAuth();
  const canManage = hasPermission("ORDER_MANAGE");

  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<OrderStatus | "">("");
  const [dueFrom, setDueFrom] = useState("");
  const [dueTo, setDueTo] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>({ field: "dueDate", direction: "asc" });
  const debouncedSearch = useDebouncedValue(search);

  const dateError = dueFrom && dueTo && dueTo < dueFrom ? "'Due to' must be on or after 'Due from'" : undefined;

  const list = useApiQuery(
    (signal) =>
      dateError
        ? Promise.resolve(undefined)
        : orderService.list(
            {
              search: debouncedSearch,
              status: status || undefined,
              dueFrom: dueFrom || undefined,
              dueTo: dueTo || undefined,
              page,
              size: PAGE_SIZE,
              sort: toSortParam(sort),
            },
            signal,
          ),
    [debouncedSearch, status, dueFrom, dueTo, dateError, page, sort],
  );

  const rows = list.data?.content ?? [];
  const onSort = (field: string) => {
    setSort((s) => nextSort(s, field));
    setPage(0);
  };
  const resetPage = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(0);
  };

  return (
    <>
      <PageHeader
        title="Orders"
        description="Customer orders from receipt to delivery."
        actions={
          canManage ? (
            <Link href="/orders/new" className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
              New order
            </Link>
          ) : undefined
        }
      />

      <div className="mb-4 flex flex-col flex-wrap gap-2 sm:flex-row sm:items-end">
        <SearchInput
          placeholder="Order number or customer"
          value={search}
          onChange={(e) => resetPage(setSearch)(e.target.value)}
          aria-label="Search orders"
        />
        <select
          value={status}
          onChange={(e) => resetPage(setStatus)(e.target.value as OrderStatus | "")}
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
          aria-label="Filter by status"
        >
          <option value="">All statuses</option>
          {ORDER_STATUSES.map((s) => (
            <option key={s} value={s}>
              {ORDER_STATUS_LABELS[s]}
            </option>
          ))}
        </select>
        <div className="flex gap-2">
          <TextField label="Due from" type="date" value={dueFrom} onChange={(e) => resetPage(setDueFrom)(e.target.value)} />
          <TextField
            label="Due to"
            type="date"
            value={dueTo}
            onChange={(e) => resetPage(setDueTo)(e.target.value)}
            error={dateError}
          />
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
                <SortableHeader label="Order no" field="orderNumber" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 font-medium">Customer</th>
                <SortableHeader label="Due date" field="dueDate" sort={sort} onSort={onSort} />
                <SortableHeader label="Status" field="status" sort={sort} onSort={onSort} />
                <SortableHeader label="Total" field="total" sort={sort} onSort={onSort} />
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Balance</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Payment</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((o) => (
                <tr
                  key={o.id}
                  className="cursor-pointer hover:bg-slate-50"
                  onClick={() => router.push(`/orders/${o.id}`)}
                >
                  <td className="whitespace-nowrap px-4 py-2.5 font-medium text-blue-700">
                    <Link href={`/orders/${o.id}`} onClick={(e) => e.stopPropagation()} className="hover:underline">
                      {o.orderNumber}
                    </Link>
                  </td>
                  <td className="px-4 py-2.5 text-slate-900">{o.customerName}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-slate-700">{formatDate(o.dueDate)}</td>
                  <td className="px-4 py-2.5">
                    <OrderStatusBadge status={o.status} />
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{formatCurrency(o.total)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{formatCurrency(o.balance)}</td>
                  <td className="px-4 py-2.5">
                    <PaymentStatusBadge status={o.paymentStatus} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {list.loading && !list.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading orders..." />
          </div>
        )}
        {!list.loading && list.data && rows.length === 0 && (
          <EmptyState
            title="No orders found"
            description={search || status || dueFrom || dueTo ? "Try a different search or filter." : undefined}
            action={canManage && !search && !status && !dueFrom && !dueTo ? <Button onClick={() => router.push("/orders/new")}>Create the first order</Button> : undefined}
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
    </>
  );
}

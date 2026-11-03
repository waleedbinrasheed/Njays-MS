"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { PageSpinner } from "@/components/ui/Spinner";
import { StatTile, compactNumber } from "@/components/ui/StatTile";
import { useAuth } from "@/features/auth/AuthProvider";
import { OrderStatusBadge } from "@/features/orders/OrderBadges";
import { useApiQuery } from "@/hooks/useApiQuery";
import { reportService } from "@/services/reportService";
import { formatCurrency, formatDate } from "@/utils/format";
import { ORDER_STATUS_LABELS } from "@/utils/labels";

const IN_PROGRESS = ["RECEIVED", "CUTTING", "STITCHING", "TRIAL"] as const;

/** Tiles whose figure the backend withheld (null) are left out entirely. */
export function DashboardPage() {
  const { user, hasPermission } = useAuth();
  const query = useApiQuery(() => reportService.dashboard(), []);
  const d = query.data;

  if (query.error && !d) return <ErrorBanner message={query.error} onRetry={query.reload} />;
  if (!d) return <PageSpinner />;

  const canOrder = hasPermission("ORDER_MANAGE");
  const canOrders = hasPermission("ORDER_VIEW");

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={`Welcome, ${user?.fullName ?? ""}. Figures for ${formatDate(d.today)}.`}
        actions={
          canOrder ? (
            <Link href="/orders/new" className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
              New Order
            </Link>
          ) : undefined
        }
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {d.dueToday !== null && (
          <StatTile label="Due today" value={compactNumber(d.dueToday)} href={canOrders ? "/orders" : undefined} />
        )}
        {d.overdue !== null && (
          <StatTile
            label="Overdue"
            value={compactNumber(d.overdue)}
            status={d.overdue > 0 ? { tone: "critical", text: "Past due date" } : undefined}
            href={canOrders ? "/orders" : undefined}
          />
        )}
        {d.readyForDelivery !== null && (
          <StatTile label="Ready for delivery" value={compactNumber(d.readyForDelivery)} href={canOrders ? "/orders" : undefined} />
        )}
        {d.ordersToday !== null && <StatTile label="Orders today" value={compactNumber(d.ordersToday)} href={canOrders ? "/orders" : undefined} />}
        {d.outstandingBalance !== null && (
          <StatTile label="Outstanding balance" value={formatCurrency(d.outstandingBalance)} href={canOrders ? "/reports/orders" : undefined} />
        )}
        {d.lowStockFabrics !== null && (
          <StatTile
            label="Fabrics to reorder"
            value={compactNumber(d.lowStockFabrics)}
            status={d.lowStockFabrics > 0 ? { tone: "warning", text: "Below reorder level" } : undefined}
            href={hasPermission("INVENTORY_VIEW") ? "/inventory" : undefined}
          />
        )}
        {d.inProgress &&
          IN_PROGRESS.map((s) => (
            <StatTile key={s} label={ORDER_STATUS_LABELS[s]} value={compactNumber(d.inProgress?.[s] ?? 0)} detail="orders" href={canOrders ? "/orders" : undefined} />
          ))}
      </div>

      <div className="mt-6 grid gap-5 xl:grid-cols-2">
        {d.recentOrders && (
          <Panel title="Recent orders" action={canOrders ? <Link href="/orders">All orders</Link> : null}>
            {d.recentOrders.length === 0 ? (
              <EmptyState title="No orders yet" action={canOrder ? <Link href="/orders/new" className="text-sm font-medium text-blue-700 hover:underline">Create the first order</Link> : undefined} />
            ) : (
              <ul className="divide-y divide-slate-100">
                {d.recentOrders.map((o) => (
                  <li key={o.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                    <div className="min-w-0">
                      <Link href={`/orders/${o.id}`} className="font-medium text-blue-700 hover:underline">
                        {o.orderNumber}
                      </Link>
                      <div className="truncate text-xs text-slate-500">
                        {o.customerName} · due {formatDate(o.dueDate)}
                      </div>
                    </div>
                    <OrderStatusBadge status={o.status} />
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        )}

      </div>
    </>
  );
}

function Panel({ title, action, children }: { title: string; action: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white">
      <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
        {action && <div className="text-sm text-blue-700">{action}</div>}
      </div>
      <div className="px-4 py-2">{children}</div>
    </section>
  );
}

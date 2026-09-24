"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Badge, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { PageSpinner } from "@/components/ui/Spinner";
import { StatTile, compactNumber } from "@/components/ui/StatTile";
import { useAuth } from "@/features/auth/AuthProvider";
import { useApiQuery } from "@/hooks/useApiQuery";
import { reportService } from "@/services/reportService";
import type { Figure, RecentDocument } from "@/types/report";
import { formatCurrency, formatDate, formatDateTime } from "@/utils/format";
import { TRANSACTION_TYPE_LABELS, formatSignedQuantity } from "@/utils/labels";

function plural(n: number, word: string) {
  return `${n.toLocaleString("en-PK")} ${word}${n === 1 ? "" : "s"}`;
}

function money(f: Figure) {
  return formatCurrency(f.total);
}

export function DashboardPage() {
  const { user, hasPermission } = useAuth();
  const query = useApiQuery((signal) => reportService.dashboard(signal), []);
  const d = query.data;

  if (query.error && !d) return <ErrorBanner message={query.error} onRetry={query.reload} />;
  if (!d) return <PageSpinner />;

  const reports = hasPermission("REPORT_VIEW");

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={`Welcome, ${user?.fullName ?? ""}. Figures for ${formatDate(d.today)}; sales and purchases count completed documents.`}
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {d.todaySales && (
          <StatTile label="Today's sales" value={money(d.todaySales)} detail={plural(d.todaySales.count, "invoice")} href="/sales" />
        )}
        {d.monthSales && (
          <StatTile label="Sales this month" value={money(d.monthSales)} detail={plural(d.monthSales.count, "invoice")} href="/sales" />
        )}
        {d.todayPurchases && (
          <StatTile
            label="Today's purchases"
            value={money(d.todayPurchases)}
            detail={plural(d.todayPurchases.count, "purchase")}
            href="/purchases"
          />
        )}
        {d.monthPurchases && (
          <StatTile
            label="Purchases this month"
            value={money(d.monthPurchases)}
            detail={plural(d.monthPurchases.count, "purchase")}
            href="/purchases"
          />
        )}
        {d.totalProducts !== null && (
          <StatTile label="Products" value={compactNumber(d.totalProducts)} detail="active" href="/inventory" />
        )}
        {d.totalQuantity !== null && <StatTile label="Units in stock" value={compactNumber(d.totalQuantity)} href="/inventory" />}
        {d.lowStockProducts !== null && (
          <StatTile
            label="Low stock"
            value={compactNumber(d.lowStockProducts)}
            status={d.lowStockProducts > 0 ? { tone: "warning", text: "Needs reorder" } : undefined}
            detail="at or below reorder level"
            href={reports ? "/reports/low-stock" : "/inventory"}
          />
        )}
        {d.outOfStockProducts !== null && (
          <StatTile
            label="Out of stock"
            value={compactNumber(d.outOfStockProducts)}
            status={d.outOfStockProducts > 0 ? { tone: "critical", text: "Cannot be sold" } : undefined}
            href="/inventory"
          />
        )}
      </div>

      <div className="mt-6 grid gap-5 xl:grid-cols-2">
        {d.lowStock && (
          <Panel title="Low-stock products" action={reports ? <Link href="/reports/low-stock">Low stock report</Link> : null}>
            {d.lowStock.length === 0 ? (
              <EmptyState title="Nothing is below its reorder level" />
            ) : (
              <table className="min-w-full text-sm">
                <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="py-2 pr-2 font-medium">Product</th>
                    <th className="py-2 pr-2 text-right font-medium">Stock</th>
                    <th className="py-2 text-right font-medium">Reorder at</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {d.lowStock.map((r) => (
                    <tr key={r.productId}>
                      <td className="py-2 pr-2">{r.title}</td>
                      <td className="py-2 pr-2 text-right tabular-nums">
                        {r.quantity === 0 ? <Badge tone="red">Out</Badge> : <span className="font-medium text-amber-700">{r.quantity}</span>}
                      </td>
                      <td className="py-2 text-right tabular-nums text-slate-600">{r.reorderLevel}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Panel>
        )}

        {d.recentMovements && (
          <Panel title="Recent stock movements" action={<Link href="/inventory/movements">All movements</Link>}>
            {d.recentMovements.length === 0 ? (
              <EmptyState title="No stock movements yet" />
            ) : (
              <ul className="divide-y divide-slate-100 text-sm">
                {d.recentMovements.map((m) => (
                  <li key={m.id} className="flex items-center justify-between gap-3 py-2">
                    <div className="min-w-0">
                      <p className="truncate text-slate-900">{m.product}</p>
                      <p className="text-xs text-slate-500">
                        {TRANSACTION_TYPE_LABELS[m.type]} · {m.user} · {formatDateTime(m.date)}
                      </p>
                    </div>
                    <span className={`shrink-0 font-semibold tabular-nums ${m.quantity > 0 ? "text-emerald-700" : "text-red-600"}`}>
                      {formatSignedQuantity(m.quantity)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        )}

        {d.recentSales && (
          <Panel title="Recent sales" action={<Link href="/sales">Sales history</Link>}>
            <RecentList rows={d.recentSales} href={(id) => `/sales/${id}`} empty="No sales yet" />
          </Panel>
        )}

        {d.recentPurchases && (
          <Panel title="Recent purchases" action={<Link href="/purchases">Purchase history</Link>}>
            <RecentList rows={d.recentPurchases} href={(id) => `/purchases/${id}`} empty="No purchases yet" />
          </Panel>
        )}
      </div>
    </>
  );
}

function Panel({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white">
      <header className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
        {action && <span className="text-sm text-blue-700 hover:underline">{action}</span>}
      </header>
      <div className="px-4 py-2">{children}</div>
    </section>
  );
}

function RecentList({ rows, href, empty }: { rows: RecentDocument[]; href: (id: number) => string; empty: string }) {
  if (rows.length === 0) return <EmptyState title={empty} />;
  return (
    <ul className="divide-y divide-slate-100 text-sm">
      {rows.map((r) => (
        <li key={r.id} className="flex items-center justify-between gap-3 py-2">
          <div className="min-w-0">
            <Link href={href(r.id)} className="font-mono text-xs font-medium text-blue-700 hover:underline">
              {r.number}
            </Link>
            {r.status === "CANCELLED" && (
              <span className="ml-2">
                <Badge>Cancelled</Badge>
              </span>
            )}
            <p className="truncate text-xs text-slate-500">
              {r.party} · {formatDate(r.date)}
            </p>
          </div>
          <span className={`shrink-0 tabular-nums ${r.status === "CANCELLED" ? "text-slate-400 line-through" : "text-slate-900"}`}>
            {formatCurrency(r.total)}
          </span>
        </li>
      ))}
    </ul>
  );
}

"use client";

import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { type Option } from "@/components/ui/AsyncSelect";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { useApiQuery } from "@/hooks/useApiQuery";
import { fabricService } from "@/services/masterDataService";
import { inventoryService } from "@/services/inventoryService";
import { formatDateTime } from "@/utils/format";
import { TRANSACTION_TYPE_LABELS, formatMetres, formatSignedQuantity } from "@/utils/labels";
import { AdjustmentForm } from "./AdjustmentForm";

/** Stock-in and adjustments. A fabric id in the URL (from Fabric Stock) preselects that fabric. */
export function StockAdjustmentPage() {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canAdjust = hasPermission("STOCK_ADJUST");
  const params = useSearchParams();
  const productId = Number(params.get("productId")) || null;
  const [page, setPage] = useState(0);
  const [formKey, setFormKey] = useState(0);

  const linked = useApiQuery(
    (signal) => (productId ? fabricService.get(productId, signal) : Promise.resolve(null)),
    [productId],
  );
  const initialProduct: Option | null = linked.data ? { id: linked.data.id, label: `${linked.data.code} · ${linked.data.name}` } : null;

  const recent = useApiQuery((signal) => inventoryService.movements({ page, size: 15 }, signal), [page, formKey]);
  const rows = recent.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Stock Adjustment"
        description="Record fabric coming in (stock-in), damage, loss, found cloth or a physical count. Every change is kept in the stock history."
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,26rem)_1fr]">
        {canAdjust ? (
          <section className="h-fit rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">New adjustment</h2>
            <AdjustmentForm
              key={`${formKey}-${productId ?? ""}`}
              initialProduct={initialProduct}
              onDone={(a) => {
                toast.success(
                  `"${a.product.name}": ${formatMetres(a.resulting - a.change)} → ${formatMetres(a.resulting)} (${formatSignedQuantity(a.change)} m)`,
                );
                setPage(0);
                setFormKey((k) => k + 1);
              }}
            />
          </section>
        ) : (
          <section className="h-fit rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
            You can view stock changes but do not have permission to make them.
          </section>
        )}

        <section className="min-w-0 overflow-hidden rounded-lg border border-slate-200 bg-white">
          <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">Recent stock changes</h2>
          {recent.error && (
            <div className="p-4">
              <ErrorBanner message={recent.error} onRetry={recent.reload} />
            </div>
          )}
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-2.5 font-medium">Date</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Fabric</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Type</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Change (m)</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Resulting (m)</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">User</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((t) => (
                  <tr key={t.id} className="align-top">
                    <td className="whitespace-nowrap px-4 py-2.5 text-slate-600">{formatDateTime(t.createdAt)}</td>
                    <td className="px-4 py-2.5">
                      <div className="font-medium text-slate-900">{t.product.name}</div>
                      {t.notes && <div className="text-xs text-slate-500">{t.notes}</div>}
                    </td>
                    <td className="px-4 py-2.5">{TRANSACTION_TYPE_LABELS[t.type]}</td>
                    <td
                      className={`px-4 py-2.5 text-right font-semibold tabular-nums ${t.change > 0 ? "text-emerald-700" : "text-red-600"}`}
                    >
                      {formatSignedQuantity(t.change)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{t.resulting.toFixed(3)}</td>
                    <td className="whitespace-nowrap px-4 py-2.5">{t.user.name}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {recent.loading && !recent.data && (
            <div className="flex justify-center py-10 text-blue-600">
              <Spinner label="Loading..." />
            </div>
          )}
          {!recent.loading && recent.data && rows.length === 0 && <EmptyState title="No stock changes yet" />}
          {recent.data && (
            <Pagination
              page={recent.data.page}
              size={recent.data.size}
              totalElements={recent.data.totalElements}
              totalPages={recent.data.totalPages}
              onPageChange={setPage}
            />
          )}
        </section>
      </div>
    </>
  );
}

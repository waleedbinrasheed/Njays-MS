"use client";

import { useState } from "react";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { useApiQuery } from "@/hooks/useApiQuery";
import { inventoryService } from "@/services/inventoryService";
import { formatDateTime } from "@/utils/format";
import { ADJUSTMENT_REASON_LABELS, formatSignedQuantity } from "@/utils/labels";
import { AdjustmentForm } from "./AdjustmentForm";

export function StockAdjustmentPage() {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canAdjust = hasPermission("STOCK_ADJUST");
  const [page, setPage] = useState(0);
  const [formKey, setFormKey] = useState(0);

  const recent = useApiQuery((signal) => inventoryService.adjustments({ page, size: 15 }, signal), [page, formKey]);
  const rows = recent.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Stock Adjustment"
        description="Correct stock for damage, loss, found items or a physical count. Every adjustment is recorded in the stock history."
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,26rem)_1fr]">
        {canAdjust ? (
          <section className="h-fit rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">New adjustment</h2>
            <AdjustmentForm
              key={formKey}
              onDone={(a) => {
                toast.success(
                  `"${a.product.title}": ${a.previousQuantity} → ${a.resultingQuantity} (${formatSignedQuantity(a.adjustmentQuantity)})`,
                );
                setPage(0);
                setFormKey((k) => k + 1);
              }}
            />
          </section>
        ) : (
          <section className="h-fit rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
            You can view adjustments but do not have permission to make them.
          </section>
        )}

        <section className="min-w-0 overflow-hidden rounded-lg border border-slate-200 bg-white">
          <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">Recent adjustments</h2>
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
                  <th scope="col" className="px-4 py-2.5 font-medium">Product</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Reason</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Change</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Before → after</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">User</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((a) => (
                  <tr key={a.id} className="align-top">
                    <td className="whitespace-nowrap px-4 py-2.5 text-slate-600">{formatDateTime(a.createdAt)}</td>
                    <td className="px-4 py-2.5">
                      <div className="font-medium text-slate-900">{a.product.title}</div>
                      {a.notes && <div className="text-xs text-slate-500">{a.notes}</div>}
                    </td>
                    <td className="px-4 py-2.5">{ADJUSTMENT_REASON_LABELS[a.reason]}</td>
                    <td
                      className={`px-4 py-2.5 text-right font-semibold tabular-nums ${
                        a.adjustmentQuantity > 0 ? "text-emerald-700" : "text-red-600"
                      }`}
                    >
                      {formatSignedQuantity(a.adjustmentQuantity)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                      {a.previousQuantity} → {a.resultingQuantity}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5">{a.user.name}</td>
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
          {!recent.loading && recent.data && rows.length === 0 && <EmptyState title="No adjustments yet" />}
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

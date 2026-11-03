"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { PageSpinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { GarmentPictorial } from "@/components/measurements/GarmentPictorial";
import { CancelDocumentDialog } from "@/features/documents/CancelDocumentDialog";
import { useAuth } from "@/features/auth/AuthProvider";
import { useApiQuery } from "@/hooks/useApiQuery";
import { ApiError } from "@/services/apiClient";
import { orderService } from "@/services/orderService";
import type { OrderStatus } from "@/types/order";
import { formatCurrency, formatDate, formatDateTime } from "@/utils/format";
import {
  EDITABLE_STATUSES,
  FABRIC_SOURCE_LABELS,
  ORDER_STATUS_LABELS,
  PAYMENT_KIND_LABELS,
  PAYMENT_METHOD_LABELS,
  STATUS_FLOW,
  formatMetres,
} from "@/utils/labels";
import { OrderStatusBadge, PaymentStatusBadge } from "./OrderBadges";
import { RecordPaymentDialog } from "./RecordPaymentDialog";

const CLOSED: OrderStatus[] = ["DELIVERED", "CANCELLED"];

export function OrderDetailPage({ id }: { id: number }) {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canManage = hasPermission("ORDER_MANAGE");
  const query = useApiQuery((signal) => orderService.get(id, signal), [id]);
  const [pendingStatus, setPendingStatus] = useState<OrderStatus | null>(null);
  const [statusBusy, setStatusBusy] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [paying, setPaying] = useState(false);

  if (query.error && !query.data) return <ErrorBanner message={query.error} onRetry={query.reload} />;
  if (!query.data) return <PageSpinner />;
  const o = query.data;

  const index = STATUS_FLOW.indexOf(o.status);
  const next = index >= 0 && index < STATUS_FLOW.length - 1 ? STATUS_FLOW[index + 1] : null;
  const back = index > 0 ? STATUS_FLOW[index - 1] : null;
  const canEdit = canManage && EDITABLE_STATUSES.includes(o.status);
  const isClosed = CLOSED.includes(o.status);
  const hasPayments = o.payments.length > 0;
  const canCancel = canManage && !isClosed && !hasPayments;
  const canPay = canManage && !isClosed && o.balance > 0;

  async function changeStatus(status: OrderStatus) {
    setStatusBusy(true);
    try {
      await orderService.changeStatus(o.id, status, "", o.version);
      toast.success(`Status changed to ${ORDER_STATUS_LABELS[status]}`);
      query.reload();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Could not change the status");
      query.reload();
    } finally {
      setStatusBusy(false);
      setPendingStatus(null);
    }
  }

  return (
    <>
      <PageHeader
        title={`Order ${o.orderNumber}`}
        description={`Customer ${o.customer.name} · created ${formatDateTime(o.createdAt)} by ${o.createdBy}`}
        actions={
          <>
            <Link href="/orders" className="inline-flex h-9 items-center rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50">
              All orders
            </Link>
            <Link href={`/orders/${o.id}/print`} className="inline-flex h-9 items-center rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50">
              Print
            </Link>
            {canEdit && (
              <Link href={`/orders/${o.id}/edit`} className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
                Edit order
              </Link>
            )}
          </>
        }
      />

      <div className="grid gap-5 lg:grid-cols-3">
        <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-4 lg:col-span-2 sm:p-5">
          <div className="flex flex-wrap items-center gap-2">
            <OrderStatusBadge status={o.status} />
            <PaymentStatusBadge status={o.paymentStatus} />
          </div>

          {canManage && !isClosed && (
            <div className="rounded-md bg-slate-50 p-3">
              <p className="mb-2 text-sm font-medium text-slate-700">Move the order forward</p>
              <div className="flex flex-wrap gap-2">
                {next && (
                  <Button onClick={() => setPendingStatus(next)} disabled={statusBusy}>
                    Mark as {ORDER_STATUS_LABELS[next]}
                  </Button>
                )}
                {back && (
                  <Button variant="secondary" onClick={() => setPendingStatus(back)} disabled={statusBusy}>
                    Move back to {ORDER_STATUS_LABELS[back]}
                  </Button>
                )}
                {canCancel && (
                  <Button variant="danger" onClick={() => setCancelling(true)}>
                    Cancel order
                  </Button>
                )}
              </div>
              {!canCancel && hasPayments && (
                <p className="mt-2 text-xs text-slate-600">This order cannot be cancelled because payments are recorded on it.</p>
              )}
            </div>
          )}

          {o.status === "CANCELLED" && (
            <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
              <strong>Cancelled.</strong> {o.cancelReason ?? ""}
            </div>
          )}

          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm sm:grid-cols-4">
            <Field label="Order date" value={formatDate(o.orderDate)} />
            <Field label="Due date" value={formatDate(o.dueDate)} />
            <Field label="Trial date" value={formatDate(o.trialDate)} />
            <Field label="Delivery date" value={formatDate(o.deliveryDate)} />
          </dl>

          <div className="rounded-md bg-slate-50 p-3 text-sm">
            <p className="font-medium text-slate-900">{o.customer.name}</p>
            <p className="text-slate-600">{o.customer.phone ?? "No phone"}</p>
            {o.customer.address && <p className="whitespace-pre-line text-slate-600">{o.customer.address}</p>}
          </div>

          {o.notes && <p className="whitespace-pre-line text-sm text-slate-700">{o.notes}</p>}
        </section>

        <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
          <h2 className="text-sm font-semibold text-slate-900">Totals</h2>
          <dl className="space-y-1 text-sm">
            <TotalRow label="Total" value={o.total} />
            <TotalRow label="Paid" value={o.paid} />
            <TotalRow label="Balance" value={o.balance} strong />
          </dl>
          {canPay && (
            <Button className="w-full" onClick={() => setPaying(true)}>
              Record payment
            </Button>
          )}
        </section>
      </div>

      <section className="mt-5 overflow-hidden rounded-lg border border-slate-200 bg-white">
        <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">Garments</h2>
        <div className="divide-y divide-slate-100">
          {o.lines.map((l) => (
            <article key={l.id} className="space-y-3 p-4">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h3 className="text-sm font-semibold text-slate-900">
                  {l.lineNo}. {l.garmentType.name}
                </h3>
                <span className="text-sm tabular-nums text-slate-700">{formatCurrency(l.lineTotal)}</span>
              </div>
              <dl className="grid gap-x-4 gap-y-1 text-sm sm:grid-cols-2">
                <Field label="Fabric" value={FABRIC_SOURCE_LABELS[l.fabricSource]} />
                {l.fabricSource === "STOCK" ? (
                  <Field
                    label="From stock"
                    value={
                      l.fabric
                        ? `${l.fabric.code} · ${l.fabric.name}${l.fabric.colour ? ` · ${l.fabric.colour}` : ""} · ${formatMetres(l.fabricMetres)} at ${formatCurrency(l.fabricPricePerMetre)} = ${formatCurrency(l.fabricAmount)}`
                        : "-"
                    }
                  />
                ) : (
                  <Field label="Customer's fabric" value={l.customerFabricNote ?? "-"} />
                )}
                <Field label="Stitching" value={formatCurrency(l.stitchingCharge)} />
                {l.notes && <Field label="Notes" value={l.notes} />}
              </dl>
              {l.measurements.length > 0 && (
                <div className="rounded-md bg-slate-50 p-3">
                  <GarmentPictorial shape={l.garmentType.shape} measurements={l.measurements} />
                </div>
              )}
            </article>
          ))}
        </div>
      </section>

      <div className="mt-5 grid gap-5 lg:grid-cols-2">
        <section className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-900">Payments</h2>
          </div>
          {o.payments.length === 0 ? (
            <EmptyState title="No payments yet" />
          ) : (
            <table className="min-w-full divide-y divide-slate-100 text-sm">
              <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-2 font-medium">Date</th>
                  <th scope="col" className="px-4 py-2 font-medium">Kind</th>
                  <th scope="col" className="px-4 py-2 font-medium">Method</th>
                  <th scope="col" className="px-4 py-2 text-right font-medium">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {o.payments.map((p) => (
                  <tr key={p.id}>
                    <td className="px-4 py-2 whitespace-nowrap">{formatDate(p.paidOn)}</td>
                    <td className="px-4 py-2">
                      {PAYMENT_KIND_LABELS[p.kind]}
                      {p.note && <div className="text-xs text-slate-500">{p.note}</div>}
                    </td>
                    <td className="px-4 py-2">{PAYMENT_METHOD_LABELS[p.method]}</td>
                    <td className="px-4 py-2 text-right tabular-nums">{formatCurrency(p.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-4">
          <h2 className="mb-3 text-sm font-semibold text-slate-900">Status history</h2>
          <ol className="space-y-2 text-sm">
            {o.statusHistory.map((h, i) => (
              <li key={i} className="flex flex-wrap gap-x-2">
                <span className="text-slate-500">{formatDateTime(h.changedAt)}</span>
                <span className="text-slate-900">
                  {h.fromStatus ? `${ORDER_STATUS_LABELS[h.fromStatus]} → ` : ""}
                  {ORDER_STATUS_LABELS[h.toStatus]}
                </span>
                <span className="text-slate-500">by {h.changedBy}</span>
                {h.note && <span className="w-full text-xs text-slate-600">{h.note}</span>}
              </li>
            ))}
          </ol>
        </section>
      </div>

      <ConfirmDialog
        open={pendingStatus !== null}
        title="Change order status?"
        message={
          pendingStatus ? (
            <>
              Move <strong>{o.orderNumber}</strong> from {ORDER_STATUS_LABELS[o.status]} to{" "}
              <strong>{ORDER_STATUS_LABELS[pendingStatus]}</strong>?
            </>
          ) : null
        }
        confirmLabel="Change status"
        loading={statusBusy}
        onConfirm={() => pendingStatus && void changeStatus(pendingStatus)}
        onCancel={() => setPendingStatus(null)}
      />

      {cancelling && (
        <CancelDocumentDialog
          title={`Cancel ${o.orderNumber}`}
          submitLabel="Cancel order"
          explanation={<>The order is kept in the list as cancelled and is left out of totals. Give the reason.</>}
          cancel={(reason) => orderService.cancel(o.id, reason, o.version)}
          onClose={() => setCancelling(false)}
          onDone={() => {
            setCancelling(false);
            toast.success(`Order ${o.orderNumber} cancelled`);
            query.reload();
          }}
        />
      )}

      {paying && (
        <RecordPaymentDialog
          order={o}
          onClose={() => setPaying(false)}
          onSaved={() => {
            setPaying(false);
            toast.success("Payment recorded");
            query.reload();
          }}
        />
      )}
    </>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="text-slate-900">{value}</dd>
    </div>
  );
}

function TotalRow({ label, value, strong = false }: { label: string; value: number; strong?: boolean }) {
  return (
    <div className={`flex justify-between ${strong ? "border-t border-slate-200 pt-2 font-semibold text-slate-900" : ""}`}>
      <dt className="text-slate-600">{label}</dt>
      <dd className="tabular-nums">{formatCurrency(value)}</dd>
    </div>
  );
}

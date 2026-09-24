"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/Feedback";
import { TextAreaField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { PageSpinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { InvoiceLayout } from "@/features/documents/InvoiceLayout";
import { PaymentStatusBadge } from "@/features/documents/StatusBadges";
import { useApiQuery } from "@/hooks/useApiQuery";
import { ApiError } from "@/services/apiClient";
import { purchaseService } from "@/services/purchaseService";
import type { PaymentStatus, PurchaseDetail } from "@/types/purchase";
import { formatDate, formatDateTime } from "@/utils/format";
import { PAYMENT_STATUS_LABELS } from "@/utils/labels";

export function PurchaseDetailPage({ id }: { id: number }) {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const [version, setVersion] = useState(0);
  const [cancelling, setCancelling] = useState(false);
  const [paymentBusy, setPaymentBusy] = useState(false);
  const query = useApiQuery((signal) => purchaseService.get(id, signal), [id, version]);
  const p = query.data;

  if (query.error && !p) return <ErrorBanner message={query.error} onRetry={query.reload} />;
  if (!p) return <PageSpinner />;

  const cancelled = p.status === "CANCELLED";
  const canEditPayment = hasPermission("PURCHASE_CREATE") && !cancelled;

  async function changePayment(status: PaymentStatus) {
    setPaymentBusy(true);
    try {
      await purchaseService.setPaymentStatus(id, status);
      toast.success(`Payment status set to ${PAYMENT_STATUS_LABELS[status]}`);
      setVersion((v) => v + 1);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Could not update payment status");
    } finally {
      setPaymentBusy(false);
    }
  }

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Link href="/purchases" className="text-sm text-slate-600 hover:text-slate-900">
          ← Purchase history
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          {canEditPayment && (
            <label className="flex items-center gap-2 text-sm text-slate-600">
              Payment
              <select
                value={p.paymentStatus}
                disabled={paymentBusy}
                onChange={(e) => void changePayment(e.target.value as PaymentStatus)}
                className="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
              >
                {Object.entries(PAYMENT_STATUS_LABELS).map(([k, label]) => (
                  <option key={k} value={k}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
          )}
          <Button variant="secondary" onClick={() => window.print()}>
            Print / Save as PDF
          </Button>
          {hasPermission("PURCHASE_CANCEL") && !cancelled && (
            <Button variant="danger" onClick={() => setCancelling(true)}>
              Cancel purchase
            </Button>
          )}
        </div>
      </div>

      {cancelled && (
        <div className="mb-4 rounded-md border border-slate-300 bg-slate-50 px-4 py-3 text-sm text-slate-700 print:hidden">
          Cancelled on {formatDateTime(p.cancelledAt)} by {p.cancelledBy?.name ?? "-"}. Reason: {p.cancelReason}
        </div>
      )}

      <InvoiceLayout
        heading="Purchase invoice"
        number={p.purchaseNumber}
        cancelled={cancelled}
        party={
          <>
            <p className="text-xs uppercase tracking-wide text-slate-500">Supplier</p>
            <p className="font-medium text-slate-900">{p.supplier.name}</p>
            {p.supplierInvoiceNumber && <p className="text-slate-600">Their invoice: {p.supplierInvoiceNumber}</p>}
          </>
        }
        meta={[
          { label: "Date", value: formatDate(p.purchaseDate) },
          { label: "Payment", value: <PaymentStatusBadge status={p.paymentStatus} /> },
          { label: "Entered by", value: p.createdBy?.name ?? "-" },
          { label: "Entered at", value: formatDateTime(p.createdAt) },
        ]}
        priceLabel="Unit cost"
        lines={p.items.map((i) => ({
          key: i.id,
          title: i.product.title,
          isbn: i.product.isbn,
          quantity: i.quantity,
          unitPrice: i.unitCost,
          discountPercent: i.discountPercent,
          taxPercent: i.taxPercent,
          lineTotal: i.lineTotal,
        }))}
        totals={p}
        footer={p.notes ? <p className="whitespace-pre-line">Notes: {p.notes}</p> : undefined}
      />

      {cancelling && (
        <CancelDialog
          purchase={p}
          onClose={() => setCancelling(false)}
          onDone={(updated) => {
            setCancelling(false);
            toast.success(`Purchase ${updated.purchaseNumber} cancelled. Stock reversed.`);
            setVersion((v) => v + 1);
          }}
        />
      )}
    </>
  );
}

function CancelDialog({
  purchase,
  onClose,
  onDone,
}: {
  purchase: PurchaseDetail;
  onClose: () => void;
  onDone: (p: PurchaseDetail) => void;
}) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const units = purchase.items.reduce((n, i) => n + i.quantity, 0);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!reason.trim()) {
      setError("Give a reason for cancelling");
      return;
    }
    setSaving(true);
    try {
      onDone(await purchaseService.cancel(purchase.id, reason));
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not cancel the purchase");
      setSaving(false);
    }
  }

  return (
    <FormDialog
      title={`Cancel ${purchase.purchaseNumber}?`}
      submitLabel="Cancel purchase"
      saving={saving}
      formError={formError}
      onSubmit={submit}
      onClose={onClose}
    >
      <p className="text-sm text-slate-600">
        {units} unit(s) will be removed from stock. This is only possible if that stock is still on hand. The purchase stays
        in history, marked as cancelled.
      </p>
      <TextAreaField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} required autoFocus />
    </FormDialog>
  );
}

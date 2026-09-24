"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/Feedback";
import { PageSpinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { CancelDocumentDialog } from "@/features/documents/CancelDocumentDialog";
import { InvoiceLayout } from "@/features/documents/InvoiceLayout";
import { PaymentStatusBadge } from "@/features/documents/StatusBadges";
import { useApiQuery } from "@/hooks/useApiQuery";
import { ApiError } from "@/services/apiClient";
import { saleService } from "@/services/saleService";
import type { PaymentStatus } from "@/types/purchase";
import { formatDate, formatDateTime } from "@/utils/format";
import { PAYMENT_STATUS_LABELS } from "@/utils/labels";

export function SaleDetailPage({ id }: { id: number }) {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const [version, setVersion] = useState(0);
  const [cancelling, setCancelling] = useState(false);
  const [paymentBusy, setPaymentBusy] = useState(false);
  const query = useApiQuery((signal) => saleService.get(id, signal), [id, version]);
  const s = query.data;

  if (query.error && !s) return <ErrorBanner message={query.error} onRetry={query.reload} />;
  if (!s) return <PageSpinner />;

  const cancelled = s.status === "CANCELLED";

  async function changePayment(status: PaymentStatus) {
    setPaymentBusy(true);
    try {
      await saleService.setPaymentStatus(id, status);
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
        <Link href="/sales" className="text-sm text-slate-600 hover:text-slate-900">
          ← Sales history
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          {hasPermission("SALE_CREATE") && !cancelled && (
            <label className="flex items-center gap-2 text-sm text-slate-600">
              Payment
              <select
                value={s.paymentStatus}
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
          {hasPermission("SALE_CANCEL") && !cancelled && (
            <Button variant="danger" onClick={() => setCancelling(true)}>
              Cancel invoice
            </Button>
          )}
          {hasPermission("SALE_CREATE") && (
            <Link href="/sales/new" className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
              New sale
            </Link>
          )}
        </div>
      </div>

      {cancelled && (
        <div className="mb-4 rounded-md border border-slate-300 bg-slate-50 px-4 py-3 text-sm text-slate-700 print:hidden">
          Cancelled on {formatDateTime(s.cancelledAt)} by {s.cancelledBy?.name ?? "-"}. Reason: {s.cancelReason}
        </div>
      )}

      <InvoiceLayout
        heading="Sales invoice"
        number={s.invoiceNumber}
        cancelled={cancelled}
        party={
          <>
            <p className="text-xs uppercase tracking-wide text-slate-500">Bill to</p>
            <p className="font-medium text-slate-900">{s.customer.name}</p>
            {s.customerPhone && <p className="text-slate-600">{s.customerPhone}</p>}
            {s.customerAddress && <p className="whitespace-pre-line text-slate-600">{s.customerAddress}</p>}
          </>
        }
        meta={[
          { label: "Date", value: formatDate(s.saleDate) },
          { label: "Payment", value: <PaymentStatusBadge status={s.paymentStatus} /> },
          { label: "Served by", value: s.createdBy?.name ?? "-" },
        ]}
        priceLabel="Unit price"
        lines={s.items.map((i) => ({
          key: i.id,
          title: i.product.title,
          isbn: i.product.isbn,
          quantity: i.quantity,
          unitPrice: i.unitPrice,
          discountPercent: i.discountPercent,
          taxPercent: i.taxPercent,
          lineTotal: i.lineTotal,
        }))}
        totals={s}
        footer={
          <>
            {s.notes && <p className="mb-2 whitespace-pre-line">Notes: {s.notes}</p>}
            <p className="text-center text-xs text-slate-500">Thank you for your purchase.</p>
          </>
        }
      />

      {cancelling && (
        <CancelDocumentDialog
          title={`Cancel ${s.invoiceNumber}?`}
          submitLabel="Cancel invoice"
          explanation={
            <>
              {s.items.reduce((n, i) => n + i.quantity, 0)} unit(s) will be returned to stock. The invoice stays in history,
              marked as cancelled. For a partial return, use Returns instead (coming in the next phase).
            </>
          }
          cancel={(reason) => saleService.cancel(s.id, reason)}
          onClose={() => setCancelling(false)}
          onDone={(updated) => {
            setCancelling(false);
            toast.success(`Invoice ${updated.invoiceNumber} cancelled. Stock returned.`);
            setVersion((v) => v + 1);
          }}
        />
      )}
    </>
  );
}

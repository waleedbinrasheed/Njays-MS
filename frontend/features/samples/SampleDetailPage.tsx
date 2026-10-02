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
import { useApiQuery } from "@/hooks/useApiQuery";
import { sampleService } from "@/services/sampleService";
import { formatCurrency, formatDate, formatDateTime } from "@/utils/format";
import { SampleSaleBanner, SampleStatusBadge } from "./SampleStatusBadge";

/** The printable sample invoice, plus what has come back and what is still out. */
export function SampleDetailPage({ id }: { id: number }) {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const [version, setVersion] = useState(0);
  const [cancelling, setCancelling] = useState(false);
  const query = useApiQuery((signal) => sampleService.get(id, signal), [id, version]);
  const s = query.data;

  if (query.error && !s) return <ErrorBanner message={query.error} onRetry={query.reload} />;
  if (!s) return <PageSpinner />;

  const cancelled = s.status === "CANCELLED";
  const canManage = hasPermission("SAMPLE_MANAGE");

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Link href="/samples" className="text-sm text-slate-600 hover:text-slate-900">
          ← Sample issues
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="secondary" onClick={() => window.print()}>
            Print / Save as PDF
          </Button>
          {canManage && !cancelled && s.outstandingQuantity > 0 && (
            <Link
              href={`/samples/${s.id}/return`}
              className="inline-flex h-9 items-center rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Return samples
            </Link>
          )}
          {canManage && !cancelled && s.returnedQuantity === 0 && (
            <Button variant="danger" onClick={() => setCancelling(true)}>
              Cancel sample
            </Button>
          )}
          {canManage && (
            <Link href="/samples/new" className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
              New sample issue
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
        heading="Sample invoice"
        number={s.sampleNumber}
        cancelled={cancelled}
        banner={<SampleSaleBanner />}
        simple
        party={
          <>
            <p className="text-xs uppercase tracking-wide text-slate-500">Issued to (representative)</p>
            <p className="font-medium text-slate-900">{s.representative.name}</p>
            {(s.representative.area || s.representative.phone) && (
              <p className="text-slate-600">{[s.representative.area, s.representative.phone].filter(Boolean).join(" · ")}</p>
            )}
            {s.representativeAddress && <p className="whitespace-pre-line text-slate-600">{s.representativeAddress}</p>}
          </>
        }
        meta={[
          { label: "Date", value: formatDate(s.issueDate) },
          { label: "Status", value: <SampleStatusBadge status={s.status} /> },
          { label: "Issued by", value: s.createdBy?.name ?? "-" },
        ]}
        priceLabel="Sample price"
        lines={s.items.map((i) => ({
          key: i.id,
          title: i.product.title,
          isbn: i.product.isbn,
          quantity: i.quantity,
          unitPrice: i.unitPrice,
          discountPercent: 0,
          taxPercent: 0,
          lineTotal: i.lineTotal,
        }))}
        totals={{ subtotal: s.total, discountTotal: 0, invoiceDiscount: 0, taxTotal: 0, total: s.total }}
        footer={
          <>
            {s.notes && <p className="mb-2 whitespace-pre-line">Notes: {s.notes}</p>}
            <p className="text-center text-xs text-slate-500">
              Samples are valued at their sample price. This document is not a retail sale invoice.
            </p>
          </>
        }
      />

      <section className="mt-6 rounded-lg border border-slate-200 bg-white print:break-inside-avoid">
        <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">Sample balance</h2>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">Book</th>
                <th className="px-4 py-2 text-right font-medium">Issued</th>
                <th className="px-4 py-2 text-right font-medium">Returned</th>
                <th className="px-4 py-2 text-right font-medium">Outstanding</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {s.items.map((i) => (
                <tr key={i.id}>
                  <td className="px-4 py-2">{i.product.title}</td>
                  <td className="whitespace-nowrap px-4 py-2 text-right tabular-nums">
                    {i.quantity} · {formatCurrency(i.lineTotal)}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2 text-right tabular-nums">
                    {i.returnedQuantity} · {formatCurrency(i.returnedAmount)}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2 text-right font-medium tabular-nums">
                    {i.outstandingQuantity} · {formatCurrency(i.outstandingAmount)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t border-slate-300 font-semibold">
              <tr>
                <td className="px-4 py-2">Total</td>
                <td className="whitespace-nowrap px-4 py-2 text-right tabular-nums">
                  {s.totalQuantity} · {formatCurrency(s.total)}
                </td>
                <td className="whitespace-nowrap px-4 py-2 text-right tabular-nums">
                  {s.returnedQuantity} · {formatCurrency(s.returnedTotal)}
                </td>
                <td className="whitespace-nowrap px-4 py-2 text-right tabular-nums">
                  {s.outstandingQuantity} · {formatCurrency(s.outstandingTotal)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      {s.returns.length > 0 && (
        <section className="mt-6 rounded-lg border border-slate-200 bg-white print:hidden">
          <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">Returns of this sample</h2>
          <ul className="divide-y divide-slate-100 text-sm">
            {s.returns.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2">
                <Link href={`/samples/returns/${r.id}`} className="font-mono text-xs font-medium text-blue-700 hover:underline">
                  {r.returnNumber}
                </Link>
                <span className="text-slate-600">{formatDate(r.returnDate)}</span>
                <span className="tabular-nums">
                  {r.totalQuantity} unit(s) · {formatCurrency(r.total)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {cancelling && (
        <CancelDocumentDialog
          title={`Cancel ${s.sampleNumber}?`}
          submitLabel="Cancel sample"
          explanation={
            <>
              {s.totalQuantity} unit(s) will be returned to stock. The sample stays in history, marked as cancelled. To record
              books coming back, use Return samples instead.
            </>
          }
          cancel={(reason) => sampleService.cancel(s.id, reason)}
          onClose={() => setCancelling(false)}
          onDone={(updated) => {
            setCancelling(false);
            toast.success(`Sample ${updated.sampleNumber} cancelled. Stock returned.`);
            setVersion((v) => v + 1);
          }}
        />
      )}
    </>
  );
}

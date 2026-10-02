"use client";

import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/Feedback";
import { PageSpinner } from "@/components/ui/Spinner";
import { InvoiceLayout } from "@/features/documents/InvoiceLayout";
import { useApiQuery } from "@/hooks/useApiQuery";
import { sampleService } from "@/services/sampleService";
import { formatDate } from "@/utils/format";
import { SampleSaleBanner } from "./SampleStatusBadge";

export function SampleReturnDetailPage({ id }: { id: number }) {
  const query = useApiQuery((signal) => sampleService.getReturn(id, signal), [id]);
  const r = query.data;

  if (query.error && !r) return <ErrorBanner message={query.error} onRetry={query.reload} />;
  if (!r) return <PageSpinner />;

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Link href="/samples/returns" className="text-sm text-slate-600 hover:text-slate-900">
          ← Sample returns
        </Link>
        <div className="flex gap-2">
          <Link
            href={`/samples/${r.sampleId}`}
            className="inline-flex h-9 items-center rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Open sample {r.sampleNumber}
          </Link>
          <Button variant="secondary" onClick={() => window.print()}>
            Print / Save as PDF
          </Button>
        </div>
      </div>

      <InvoiceLayout
        heading="Sample return"
        number={r.returnNumber}
        cancelled={false}
        banner={<SampleSaleBanner />}
        simple
        party={
          <>
            <p className="text-xs uppercase tracking-wide text-slate-500">Returned by (representative)</p>
            <p className="font-medium text-slate-900">{r.representative.name}</p>
            {(r.representative.area || r.representative.phone) && (
              <p className="text-slate-600">{[r.representative.area, r.representative.phone].filter(Boolean).join(" · ")}</p>
            )}
          </>
        }
        meta={[
          { label: "Return date", value: formatDate(r.returnDate) },
          { label: "Sample", value: <span className="font-mono">{r.sampleNumber}</span> },
          { label: "Issued on", value: formatDate(r.issueDate) },
          { label: "Recorded by", value: r.createdBy?.name ?? "-" },
        ]}
        priceLabel="Sample price"
        lines={r.items.map((i) => ({
          key: i.id,
          title: i.product.title,
          isbn: i.product.isbn,
          quantity: i.quantity,
          unitPrice: i.unitPrice,
          discountPercent: 0,
          taxPercent: 0,
          lineTotal: i.lineTotal,
        }))}
        totals={{ subtotal: r.total, discountTotal: 0, invoiceDiscount: 0, taxTotal: 0, total: r.total }}
        footer={r.notes ? <p className="whitespace-pre-line">Notes: {r.notes}</p> : undefined}
      />
    </>
  );
}

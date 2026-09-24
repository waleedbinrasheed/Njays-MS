"use client";

import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/Feedback";
import { PageSpinner } from "@/components/ui/Spinner";
import { useApiQuery } from "@/hooks/useApiQuery";
import { returnServices } from "@/services/returnService";
import { settingsService } from "@/services/purchaseService";
import type { ReturnKind } from "@/types/returns";
import { formatCurrency, formatDate, formatDateTime } from "@/utils/format";
import { RETURN_KINDS } from "./returnKinds";

export function ReturnDetailPage({ kind, id }: { kind: ReturnKind; id: number }) {
  const cfg = RETURN_KINDS[kind];
  const query = useApiQuery((signal) => returnServices[kind].get(id, signal), [kind, id]);
  const business = useApiQuery((signal) => settingsService.business(signal), []);
  const r = query.data;

  if (query.error && !r) return <ErrorBanner message={query.error} onRetry={query.reload} />;
  if (!r) return <PageSpinner />;
  const b = business.data;

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Link href={cfg.historyHref} className="text-sm text-slate-600 hover:text-slate-900">
          ← {cfg.title}
        </Link>
        <div className="flex gap-2">
          <Link
            href={cfg.documentHref(r.documentId)}
            className="inline-flex h-9 items-center rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            View {cfg.documentLabel.toLowerCase()} {r.documentNumber}
          </Link>
          <Button variant="secondary" onClick={() => window.print()}>
            Print / Save as PDF
          </Button>
        </div>
      </div>

      <article className="rounded-lg border border-slate-200 bg-white p-6 print:border-0 print:p-0 sm:p-8">
        <header className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:justify-between">
          <div>
            <p className="text-lg font-semibold text-slate-900">{b?.name ?? ""}</p>
            {b?.address && <p className="whitespace-pre-line text-sm text-slate-600">{b.address}</p>}
            <p className="text-sm text-slate-600">{[b?.phone, b?.email].filter(Boolean).join(" · ")}</p>
          </div>
          <div className="sm:text-right">
            <p className="text-xl font-semibold uppercase tracking-wide text-slate-900">{cfg.printHeading}</p>
            <p className="font-mono text-sm text-slate-700">{r.returnNumber}</p>
          </div>
        </header>

        <section className="grid gap-4 py-5 text-sm sm:grid-cols-2">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500">{cfg.partyLabel}</p>
            <p className="font-medium text-slate-900">{r.party.name}</p>
            <p className="mt-2 text-slate-600">
              Against {cfg.documentLabel.toLowerCase()} <span className="font-mono">{r.documentNumber}</span> of {formatDate(r.documentDate)}
            </p>
          </div>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 sm:justify-self-end">
            <dt className="text-slate-500">Return date</dt>
            <dd>{formatDate(r.returnDate)}</dd>
            <dt className="text-slate-500">Reason</dt>
            <dd>{r.reason}</dd>
            <dt className="text-slate-500">Recorded by</dt>
            <dd>{r.createdBy?.name ?? "-"}</dd>
            <dt className="text-slate-500">Recorded at</dt>
            <dd>{formatDateTime(r.createdAt)}</dd>
          </dl>
        </section>

        <table className="w-full text-sm">
          <thead className="border-y border-slate-300 text-left text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="py-2 pr-2 font-medium">#</th>
              <th className="py-2 pr-2 font-medium">Item</th>
              <th className="py-2 pr-2 text-right font-medium">Qty</th>
              <th className="py-2 text-right font-medium">Amount</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {r.items.map((i, index) => (
              <tr key={i.id}>
                <td className="py-2 pr-2 text-slate-500">{index + 1}</td>
                <td className="py-2 pr-2">
                  <div>{i.product.title}</div>
                  {i.product.isbn && <div className="font-mono text-xs text-slate-500">{i.product.isbn}</div>}
                </td>
                <td className="py-2 pr-2 text-right tabular-nums">{i.quantity}</td>
                <td className="py-2 text-right tabular-nums">{formatCurrency(i.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="mt-4 flex justify-end border-t border-slate-300 pt-3 text-base font-semibold text-slate-900">
          <span className="mr-8">{cfg.amountLabel} total</span>
          <span className="tabular-nums">{formatCurrency(r.total)}</span>
        </div>
        {r.notes && <p className="mt-6 whitespace-pre-line border-t border-slate-200 pt-4 text-sm text-slate-600">Notes: {r.notes}</p>}
      </article>
    </>
  );
}

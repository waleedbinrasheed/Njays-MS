"use client";

import type { ReactNode } from "react";
import { BrandLogo } from "@/components/brand/Brand";
import { useApiQuery } from "@/hooks/useApiQuery";
import { settingsService } from "@/services/purchaseService";
import { formatCurrency } from "@/utils/format";

export interface InvoiceLineView {
  key: number;
  title: string;
  isbn: string | null;
  quantity: number;
  unitPrice: number;
  discountPercent: number;
  taxPercent: number;
  lineTotal: number;
}

export interface InvoiceLayoutProps {
  heading: string;
  number: string;
  cancelled: boolean;
  /** Left block: supplier / customer details. */
  party: ReactNode;
  /** Right block: date, status, created by... */
  meta: { label: string; value: ReactNode }[];
  priceLabel: string;
  lines: InvoiceLineView[];
  totals: { subtotal: number; discountTotal: number; invoiceDiscount: number; taxTotal: number; total: number };
  footer?: ReactNode;
  /** Shown prominently under the header (e.g. "SAMPLE SALE — Not Retail Sale"). */
  banner?: ReactNode;
  /** Quantity x price only: no discount/tax columns, and only the total underneath. */
  simple?: boolean;
}

/** Printable document body shared by purchase and sales invoices (A4 friendly via print styles). */
export function InvoiceLayout({ heading, number, cancelled, party, meta, priceLabel, lines, totals, footer, banner, simple = false }: InvoiceLayoutProps) {
  const business = useApiQuery((signal) => settingsService.business(signal), []);
  const b = business.data;

  return (
    <article className="relative rounded-lg border border-slate-200 bg-white p-6 print:border-0 print:p-0 sm:p-8">
      {cancelled && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center" aria-hidden>
          <span className="-rotate-12 rounded border-4 border-red-300 px-6 py-2 text-5xl font-bold uppercase tracking-widest text-red-300/80">
            Cancelled
          </span>
        </div>
      )}

      <header className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:justify-between">
        <div className="flex items-start gap-4">
          <BrandLogo className="h-20 w-auto shrink-0" />
          <div>
            <p className="text-lg font-semibold text-slate-900">{b?.name ?? ""}</p>
            {b?.address && <p className="whitespace-pre-line text-sm text-slate-600">{b.address}</p>}
            <p className="text-sm text-slate-600">{[b?.phone, b?.email].filter(Boolean).join(" · ")}</p>
          </div>
        </div>
        <div className="sm:text-right">
          <p className="text-xl font-semibold uppercase tracking-wide text-slate-900">{heading}</p>
          <p className="font-mono text-sm text-slate-700">{number}</p>
        </div>
      </header>

      {banner && <div className="mt-4">{banner}</div>}

      <section className="grid gap-4 py-5 text-sm sm:grid-cols-2">
        <div>{party}</div>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 sm:justify-self-end">
          {meta.map((m) => (
            <div key={m.label} className="contents">
              <dt className="text-slate-500">{m.label}</dt>
              <dd className="text-slate-900">{m.value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <table className="w-full text-sm">
        <thead className="border-y border-slate-300 text-left text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th className="py-2 pr-2 font-medium">#</th>
            <th className="py-2 pr-2 font-medium">Item</th>
            <th className="py-2 pr-2 text-right font-medium">Qty</th>
            <th className="py-2 pr-2 text-right font-medium">{priceLabel}</th>
            {!simple && (
              <>
                <th className="py-2 pr-2 text-right font-medium">Disc %</th>
                <th className="py-2 pr-2 text-right font-medium">Tax %</th>
              </>
            )}
            <th className="py-2 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {lines.map((l, i) => (
            <tr key={l.key} className="align-top">
              <td className="py-2 pr-2 text-slate-500">{i + 1}</td>
              <td className="py-2 pr-2">
                <div className="text-slate-900">{l.title}</div>
                {l.isbn && <div className="font-mono text-xs text-slate-500">{l.isbn}</div>}
              </td>
              <td className="py-2 pr-2 text-right tabular-nums">{l.quantity}</td>
              <td className="whitespace-nowrap py-2 pr-2 text-right tabular-nums">{formatCurrency(l.unitPrice)}</td>
              {!simple && (
                <>
                  <td className="py-2 pr-2 text-right tabular-nums">{Number(l.discountPercent) ? l.discountPercent : "-"}</td>
                  <td className="py-2 pr-2 text-right tabular-nums">{Number(l.taxPercent) ? l.taxPercent : "-"}</td>
                </>
              )}
              <td className="whitespace-nowrap py-2 text-right tabular-nums">{formatCurrency(l.lineTotal)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <section className="mt-4 flex justify-end border-t border-slate-300 pt-3">
        <dl className="grid w-full max-w-xs grid-cols-2 gap-y-1 text-sm">
          {!simple && (
            <>
              <dt className="text-slate-600">Subtotal</dt>
              <dd className="text-right tabular-nums">{formatCurrency(totals.subtotal)}</dd>
              <dt className="text-slate-600">Discounts</dt>
              <dd className="text-right tabular-nums">− {formatCurrency(totals.discountTotal)}</dd>
              {Number(totals.invoiceDiscount) > 0 && (
                <>
                  <dt className="pl-3 text-xs text-slate-500">incl. invoice discount</dt>
                  <dd className="text-right text-xs tabular-nums text-slate-500">{formatCurrency(totals.invoiceDiscount)}</dd>
                </>
              )}
              <dt className="text-slate-600">Tax</dt>
              <dd className="text-right tabular-nums">+ {formatCurrency(totals.taxTotal)}</dd>
            </>
          )}
          <dt className="mt-2 border-t border-slate-300 pt-2 text-base font-semibold text-slate-900">Total</dt>
          <dd className="mt-2 border-t border-slate-300 pt-2 text-right text-base font-semibold tabular-nums text-slate-900">
            {formatCurrency(totals.total)}
          </dd>
        </dl>
      </section>

      {footer && <footer className="mt-6 border-t border-slate-200 pt-4 text-sm text-slate-600">{footer}</footer>}
    </article>
  );
}

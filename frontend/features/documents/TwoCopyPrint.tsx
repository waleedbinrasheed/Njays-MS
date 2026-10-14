"use client";

import { BrandLogo } from "@/components/brand/Brand";
import { useApiQuery } from "@/hooks/useApiQuery";
import { settingsService } from "@/services/purchaseService";
import type { BusinessDetails } from "@/types/purchase";
import { formatCurrency } from "@/utils/format";
import type { InvoiceLayoutProps } from "./InvoiceLayout";

/**
 * Prints the invoice twice on one A4 page (office copy on top, representative copy below, dashed
 * cut line between). If a copy does not fit in half a page, each copy gets its own page instead.
 * The two-copy block is laid out off-screen at print width so it can be measured before printing.
 */
export function printTwoCopies() {
  const root = document.documentElement;
  const halves = Array.from(document.querySelectorAll<HTMLElement>("[data-two-copy] .copy-half"));
  // Keep ~2mm spare: printed text can come out a little taller than on screen.
  const overflows = halves.some((h) => {
    const contentHeight = (h.firstElementChild as HTMLElement | null)?.offsetHeight ?? 0;
    return contentHeight > h.clientHeight - 8;
  });
  root.dataset.printMode = overflows ? "two-full" : "two";
  const reset = () => {
    delete root.dataset.printMode;
    window.removeEventListener("afterprint", reset);
  };
  window.addEventListener("afterprint", reset);
  window.print();
}

/** Off-screen until "Print 2 copies"; the on-screen invoice (marked print-single) prints otherwise. */
export function TwoCopyPrint(props: InvoiceLayoutProps) {
  const business = useApiQuery((signal) => settingsService.business(signal), []);
  return (
    <div data-two-copy className="two-copy-print" aria-hidden>
      <div className="copy-half">
        <CompactInvoice {...props} business={business.data ?? null} copyLabel="Office copy" />
      </div>
      <div className="cut-line">
        <span aria-hidden>✂</span>
        <span className="cut-line-rule" />
        <span>cut here</span>
        <span className="cut-line-rule" />
      </div>
      <div className="copy-half">
        <CompactInvoice {...props} business={business.data ?? null} copyLabel="Representative copy" />
      </div>
    </div>
  );
}

function CompactInvoice({
  heading,
  number,
  cancelled,
  party,
  meta,
  priceLabel,
  lines,
  totals,
  footer,
  banner,
  simple = false,
  business: b,
  copyLabel,
}: InvoiceLayoutProps & { business: BusinessDetails | null; copyLabel: string }) {
  return (
    <article className="relative text-[11px] leading-snug text-slate-900">
      {cancelled && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center" aria-hidden>
          <span className="-rotate-12 rounded border-4 border-red-300 px-4 py-1 text-3xl font-bold uppercase tracking-widest text-red-300">
            Cancelled
          </span>
        </div>
      )}

      <header className="flex items-start justify-between gap-4 border-b border-slate-400 pb-1.5">
        <div className="flex min-w-0 items-center gap-2">
          <BrandLogo className="h-12 w-auto shrink-0" />
          <div className="min-w-0">
            <p className="text-sm font-semibold">{b?.name ?? ""}</p>
            <p className="text-slate-600">{[b?.address?.replace(/\s*\n\s*/g, ", "), b?.phone, b?.email].filter(Boolean).join(" · ")}</p>
          </div>
        </div>
        <div className="shrink-0 text-right">
          <p className="rounded border border-slate-500 px-1.5 text-[10px] font-semibold uppercase tracking-wider">{copyLabel}</p>
          <p className="mt-0.5 text-sm font-semibold uppercase">{heading}</p>
          <p className="font-mono">{number}</p>
        </div>
      </header>

      {banner && <div className="mt-1.5 [&_div]:py-0.5 [&_p]:text-[11px]">{banner}</div>}

      <section className="grid grid-cols-[1fr_auto] gap-4 py-1.5 [&_p]:text-[11px] [&_p]:leading-snug">
        <div>{party}</div>
        <dl className="grid grid-cols-[auto_auto] gap-x-3">
          {meta.map((m) => (
            <div key={m.label} className="contents">
              <dt className="text-slate-500">{m.label}</dt>
              <dd className="[&_span]:text-[10px]">{m.value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <table className="w-full">
        <thead className="border-y border-slate-400 text-left text-[10px] uppercase text-slate-600">
          <tr>
            <th className="py-0.5 pr-1 font-medium">#</th>
            <th className="py-0.5 pr-1 font-medium">Item</th>
            <th className="py-0.5 pr-1 text-right font-medium">Qty</th>
            <th className="py-0.5 pr-1 text-right font-medium">{priceLabel}</th>
            {!simple && (
              <>
                <th className="py-0.5 pr-1 text-right font-medium">Disc %</th>
                <th className="py-0.5 pr-1 text-right font-medium">Tax %</th>
              </>
            )}
            <th className="py-0.5 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {lines.map((l, i) => (
            <tr key={l.key}>
              <td className="py-0.5 pr-1 text-slate-500">{i + 1}</td>
              <td className="py-0.5 pr-1">
                {l.title}
                {l.isbn && <span className="ml-1 font-mono text-[10px] text-slate-500">{l.isbn}</span>}
              </td>
              <td className="py-0.5 pr-1 text-right tabular-nums">{l.quantity}</td>
              <td className="whitespace-nowrap py-0.5 pr-1 text-right tabular-nums">{formatCurrency(l.unitPrice)}</td>
              {!simple && (
                <>
                  <td className="py-0.5 pr-1 text-right tabular-nums">{Number(l.discountPercent) ? l.discountPercent : "-"}</td>
                  <td className="py-0.5 pr-1 text-right tabular-nums">{Number(l.taxPercent) ? l.taxPercent : "-"}</td>
                </>
              )}
              <td className="whitespace-nowrap py-0.5 text-right tabular-nums">{formatCurrency(l.lineTotal)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <section className="mt-1 flex justify-end border-t border-slate-400 pt-1">
        <dl className="grid w-64 grid-cols-2">
          {!simple && (
            <>
              <dt className="text-slate-600">Subtotal</dt>
              <dd className="text-right tabular-nums">{formatCurrency(totals.subtotal)}</dd>
              <dt className="text-slate-600">Discounts</dt>
              <dd className="text-right tabular-nums">− {formatCurrency(totals.discountTotal)}</dd>
              <dt className="text-slate-600">Tax</dt>
              <dd className="text-right tabular-nums">+ {formatCurrency(totals.taxTotal)}</dd>
            </>
          )}
          <dt className="font-semibold">Total</dt>
          <dd className="text-right font-semibold tabular-nums">{formatCurrency(totals.total)}</dd>
        </dl>
      </section>

      {footer && <footer className="mt-1 text-[10px] text-slate-600 [&_p]:mb-0 [&_p]:text-[10px]">{footer}</footer>}

      <div className="mt-4 flex justify-between gap-8 text-[10px] text-slate-600">
        <span className="flex-1 border-t border-slate-400 pt-0.5">Issued by (signature)</span>
        <span className="flex-1 border-t border-slate-400 pt-0.5">Received by representative (signature)</span>
      </div>
    </article>
  );
}

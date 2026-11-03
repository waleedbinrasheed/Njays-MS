import { GarmentPictorial } from "@/components/measurements/GarmentPictorial";
import type { Order } from "@/types/order";
import type { BusinessDetails } from "@/types/settings";
import { formatCurrency, formatDate } from "@/utils/format";
import { FABRIC_SOURCE_LABELS, ORDER_STATUS_LABELS, formatMetres } from "@/utils/labels";

interface OrderInvoiceProps {
  order: Order;
  business: BusinessDetails | null;
  /** "Office copy" / "Customer copy" when printed as two copies. */
  copyLabel?: string;
  /** Smaller text and drawings, so two copies fit on one A4 page. */
  compact?: boolean;
}

/** Printed order: shop details, customer, garments with fabric, stitching and measurements, totals. */
export function OrderInvoice({ order: o, business: b, copyLabel, compact = false }: OrderInvoiceProps) {
  const text = compact ? "text-[11px] leading-snug" : "text-sm";
  const small = compact ? "text-[10px]" : "text-xs";
  const pad = compact ? "py-0.5" : "py-2";

  return (
    <article className={`relative bg-white text-slate-900 ${text}`}>
      {o.status === "CANCELLED" && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center" aria-hidden>
          <span className="-rotate-12 rounded border-4 border-red-300 px-4 py-1 text-3xl font-bold uppercase tracking-widest text-red-300">
            Cancelled
          </span>
        </div>
      )}

      <header className="flex items-start justify-between gap-4 border-b border-slate-400 pb-1.5">
        <div className="min-w-0">
          <p className={`${compact ? "text-sm" : "text-lg"} font-semibold`}>{b?.name ?? ""}</p>
          <p className="text-slate-600">{[b?.address?.replace(/\s*\n\s*/g, ", "), b?.phone, b?.email].filter(Boolean).join(" · ")}</p>
        </div>
        <div className="shrink-0 text-right">
          {copyLabel && (
            <p className={`inline-block rounded border border-slate-500 px-1.5 ${small} font-semibold uppercase tracking-wider`}>{copyLabel}</p>
          )}
          <p className={`mt-0.5 font-semibold uppercase ${compact ? "text-sm" : "text-base"}`}>Order</p>
          <p className="font-mono">{o.orderNumber}</p>
        </div>
      </header>

      <section className={`grid grid-cols-[1fr_auto] gap-4 ${pad}`}>
        <div>
          <p className="font-medium">{o.customer.name}</p>
          {o.customer.phone && <p className="text-slate-600">Phone: {o.customer.phone}</p>}
          {o.customer.address && <p className="whitespace-pre-line text-slate-600">{o.customer.address}</p>}
        </div>
        <dl className="grid grid-cols-[auto_auto] gap-x-3">
          <dt className="text-slate-500">Order date</dt>
          <dd className="text-right">{formatDate(o.orderDate)}</dd>
          <dt className="text-slate-500">Due date</dt>
          <dd className="text-right font-medium">{formatDate(o.dueDate)}</dd>
          {o.trialDate && (
            <>
              <dt className="text-slate-500">Trial</dt>
              <dd className="text-right">{formatDate(o.trialDate)}</dd>
            </>
          )}
          {o.deliveryDate && (
            <>
              <dt className="text-slate-500">Delivery</dt>
              <dd className="text-right">{formatDate(o.deliveryDate)}</dd>
            </>
          )}
          <dt className="text-slate-500">Status</dt>
          <dd className="text-right">{ORDER_STATUS_LABELS[o.status]}</dd>
        </dl>
      </section>

      {o.lines.map((l) => (
        <div key={l.id} className={`border-t border-slate-300 ${pad} ${compact ? "space-y-1" : "space-y-2"}`}>
          <table className="w-full">
            <thead className={`text-left ${small} uppercase text-slate-600`}>
              <tr>
                <th className="py-0.5 pr-1 font-medium">#</th>
                <th className="py-0.5 pr-1 font-medium">Garment</th>
                <th className="py-0.5 pr-1 font-medium">Fabric</th>
                <th className="py-0.5 pr-1 text-right font-medium">Stitching</th>
                <th className="py-0.5 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody>
              <tr className="align-top">
                <td className="py-0.5 pr-1 text-slate-500">{l.lineNo}</td>
                <td className="py-0.5 pr-1 font-medium">{l.garmentType.name}</td>
                <td className="py-0.5 pr-1">
                  {l.fabricSource === "STOCK" ? (
                    <>
                      {l.fabric ? `${l.fabric.code} · ${l.fabric.name}${l.fabric.colour ? ` · ${l.fabric.colour}` : ""}` : "-"}
                      <div className={`${small} text-slate-600`}>
                        {formatMetres(l.fabricMetres)} at {formatCurrency(l.fabricPricePerMetre)} = {formatCurrency(l.fabricAmount)}
                      </div>
                    </>
                  ) : (
                    <>
                      {FABRIC_SOURCE_LABELS.CUSTOMER}
                      {l.customerFabricNote && <div className={`${small} text-slate-600`}>{l.customerFabricNote}</div>}
                    </>
                  )}
                </td>
                <td className="whitespace-nowrap py-0.5 pr-1 text-right tabular-nums">{formatCurrency(l.stitchingCharge)}</td>
                <td className="whitespace-nowrap py-0.5 text-right font-medium tabular-nums">{formatCurrency(l.lineTotal)}</td>
              </tr>
            </tbody>
          </table>
          {l.notes && <p className={`${small} text-slate-600`}>Note: {l.notes}</p>}
          {l.measurements.length > 0 ? (
            <div>
              <p className={`${small} font-semibold uppercase text-slate-600`}>Measurements (inches)</p>
              <GarmentPictorial shape={l.garmentType.shape} measurements={l.measurements} compact={compact} />
            </div>
          ) : (
            <p className={`${small} text-slate-500`}>No measurements recorded for this garment.</p>
          )}
        </div>
      ))}

      <section className={`mt-1 flex justify-end border-t border-slate-400 pt-1`}>
        <dl className={`grid ${compact ? "w-56" : "w-72"} grid-cols-2`}>
          <dt className="font-semibold">Total</dt>
          <dd className="text-right font-semibold tabular-nums">{formatCurrency(o.total)}</dd>
          <dt className="text-slate-600">Paid</dt>
          <dd className="text-right tabular-nums">{formatCurrency(o.paid)}</dd>
          <dt className="font-semibold">Balance due</dt>
          <dd className="text-right font-semibold tabular-nums">{formatCurrency(o.balance)}</dd>
        </dl>
      </section>

      {o.notes && <p className={`mt-1 ${small} text-slate-600`}>Notes: {o.notes}</p>}

      <div className={`mt-4 flex justify-between gap-8 ${small} text-slate-600`}>
        <span className="flex-1 border-t border-slate-400 pt-0.5">Taken by (signature)</span>
        <span className="flex-1 border-t border-slate-400 pt-0.5">Received by customer (signature)</span>
      </div>
    </article>
  );
}

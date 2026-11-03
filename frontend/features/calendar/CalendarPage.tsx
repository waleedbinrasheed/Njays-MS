"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { Spinner } from "@/components/ui/Spinner";
import { OrderStatusBadge } from "@/features/orders/OrderBadges";
import { useApiQuery } from "@/hooks/useApiQuery";
import { orderService } from "@/services/orderService";
import type { CalendarDateType, CalendarEntry } from "@/types/order";
import { todayIso } from "@/utils/format";
import { CALENDAR_DATE_LABELS } from "@/utils/labels";

const DATE_TYPES: CalendarDateType[] = ["DUE", "TRIAL", "DELIVERY"];

/** Colour per date type; each chip also carries its text label, so colour is never the only cue. */
const CHIP: Record<CalendarDateType, string> = {
  DUE: "bg-blue-100 text-blue-900",
  TRIAL: "bg-amber-100 text-amber-900",
  DELIVERY: "bg-emerald-100 text-emerald-900",
};
const DOT: Record<CalendarDateType, string> = {
  DUE: "bg-blue-600",
  TRIAL: "bg-amber-500",
  DELIVERY: "bg-emerald-600",
};
const SHORT: Record<CalendarDateType, string> = { DUE: "Due", TRIAL: "Trial", DELIVERY: "Delivery" };

const pad = (n: number) => String(n).padStart(2, "0");
/** Local calendar date as yyyy-mm-dd (no time-zone shift). */
const toIso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const parseIso = (s: string) => {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d);
};
const addDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
const monthStart = (d: Date) => new Date(d.getFullYear(), d.getMonth(), 1);

/** Six weeks starting on Sunday, so every month fits the same grid (42 days, well under the 93-day limit). */
function gridDays(month: Date): Date[] {
  const start = addDays(monthStart(month), -monthStart(month).getDay());
  return Array.from({ length: 42 }, (_, i) => addDays(start, i));
}

const WEEKDAYS = Array.from({ length: 7 }, (_, i) =>
  new Date(2024, 0, 7 + i).toLocaleDateString("en-PK", { weekday: "short" }),
);

export function CalendarPage() {
  const [month, setMonth] = useState(() => monthStart(parseIso(todayIso())));
  const [selected, setSelected] = useState(todayIso());
  const [types, setTypes] = useState<Record<CalendarDateType, boolean>>({ DUE: true, TRIAL: true, DELIVERY: true });

  const days = useMemo(() => gridDays(month), [month]);
  const from = toIso(days[0]);
  const to = toIso(days[days.length - 1]);

  const calendar = useApiQuery((signal) => orderService.calendar(from, to, signal), [from, to]);

  const byDate = useMemo(() => {
    const map = new Map<string, CalendarEntry[]>();
    for (const e of calendar.data ?? []) {
      if (!types[e.dateType]) continue;
      map.set(e.date, [...(map.get(e.date) ?? []), e]);
    }
    return map;
  }, [calendar.data, types]);

  const dayEntries = byDate.get(selected) ?? [];
  const today = todayIso();

  return (
    <>
      <PageHeader
        title="Calendar"
        description="Due, trial and delivery dates of orders. Select a day to see its orders."
        actions={
          <>
            <Button variant="secondary" onClick={() => setMonth((m) => new Date(m.getFullYear(), m.getMonth() - 1, 1))} aria-label="Previous month">
              ‹ Previous
            </Button>
            <Button
              variant="secondary"
              onClick={() => {
                setMonth(monthStart(parseIso(today)));
                setSelected(today);
              }}
            >
              Today
            </Button>
            <Button variant="secondary" onClick={() => setMonth((m) => new Date(m.getFullYear(), m.getMonth() + 1, 1))} aria-label="Next month">
              Next ›
            </Button>
          </>
        }
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-slate-900">
          {month.toLocaleDateString("en-PK", { month: "long", year: "numeric" })}
        </h2>
        <div className="flex flex-wrap items-center gap-4 text-sm">
          {DATE_TYPES.map((t) => (
            <label key={t} className="flex items-center gap-1.5">
              <input
                type="checkbox"
                className="accent-blue-600"
                checked={types[t]}
                onChange={(e) => setTypes((s) => ({ ...s, [t]: e.target.checked }))}
              />
              <span className={`inline-block h-2.5 w-2.5 rounded-full ${DOT[t]}`} aria-hidden />
              {CALENDAR_DATE_LABELS[t]}
            </label>
          ))}
          {calendar.loading && <Spinner size="sm" />}
        </div>
      </div>

      {calendar.error && (
        <div className="mb-4">
          <ErrorBanner message={calendar.error} onRetry={calendar.reload} />
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <div className="grid grid-cols-7 border-b border-slate-200 bg-slate-50 text-center text-xs font-medium uppercase tracking-wide text-slate-500">
          {WEEKDAYS.map((w) => (
            <div key={w} className="py-2">
              {w}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7">
          {days.map((d) => {
            const iso = toIso(d);
            const inMonth = d.getMonth() === month.getMonth();
            const entries = byDate.get(iso) ?? [];
            const isSelected = iso === selected;
            const isToday = iso === today;
            return (
              <div
                key={iso}
                className={`min-h-20 min-w-0 border-b border-r border-slate-100 p-1 sm:min-h-28 sm:p-1.5 ${inMonth ? "bg-white" : "bg-slate-50"} ${
                  isSelected ? "ring-2 ring-inset ring-blue-500" : ""
                }`}
              >
                <button
                  type="button"
                  onClick={() => setSelected(iso)}
                  aria-pressed={isSelected}
                  aria-label={`${d.toLocaleDateString("en-PK", { weekday: "long", day: "numeric", month: "long" })}, ${entries.length} item${entries.length === 1 ? "" : "s"}`}
                  className={`mb-1 flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium tabular-nums ${
                    isToday ? "bg-blue-600 text-white" : inMonth ? "text-slate-900 hover:bg-slate-100" : "text-slate-400 hover:bg-slate-100"
                  }`}
                >
                  {d.getDate()}
                </button>
                <div className="hidden space-y-0.5 sm:block">
                  {entries.slice(0, 3).map((e, i) => (
                    <Link
                      key={`${e.orderId}-${e.dateType}-${i}`}
                      href={`/orders/${e.orderId}`}
                      className={`block truncate rounded px-1 py-0.5 text-[11px] leading-tight hover:underline ${CHIP[e.dateType]}`}
                      title={`${SHORT[e.dateType]} · ${e.orderNumber} · ${e.customerName}`}
                    >
                      {SHORT[e.dateType]}: {e.customerName}
                    </Link>
                  ))}
                  {entries.length > 3 && (
                    <button
                      type="button"
                      onClick={() => setSelected(iso)}
                      className="px-1 text-[11px] font-medium text-blue-700 hover:underline"
                    >
                      +{entries.length - 3} more
                    </button>
                  )}
                </div>
                <div className="flex flex-wrap items-center gap-0.5 sm:hidden" aria-hidden>
                  {entries.slice(0, 4).map((e, i) => (
                    <span key={i} className={`inline-block h-1.5 w-1.5 rounded-full ${DOT[e.dateType]}`} />
                  ))}
                  {entries.length > 4 && <span className="text-[10px] font-medium text-slate-600">+{entries.length - 4}</span>}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <section className="mt-6 overflow-hidden rounded-lg border border-slate-200 bg-white">
        <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">
          {parseIso(selected).toLocaleDateString("en-PK", { weekday: "long", day: "numeric", month: "long", year: "numeric" })}
          <span className="ml-2 font-normal text-slate-500">
            · {dayEntries.length} {dayEntries.length === 1 ? "date" : "dates"}
          </span>
        </h2>
        {dayEntries.length === 0 ? (
          <EmptyState title="Nothing scheduled for this day" description="Choose another day, or change the date filters above." />
        ) : (
          <ul className="max-h-[32rem] divide-y divide-slate-100 overflow-y-auto">
            {dayEntries.map((e, i) => (
              <li key={`${e.orderId}-${e.dateType}-${i}`} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm">
                <div className="flex min-w-0 items-center gap-3">
                  <span className={`inline-flex shrink-0 rounded px-2 py-0.5 text-xs font-medium ${CHIP[e.dateType]}`}>
                    {CALENDAR_DATE_LABELS[e.dateType]}
                  </span>
                  <div className="min-w-0">
                    <Link href={`/orders/${e.orderId}`} className="font-medium text-blue-700 hover:underline">
                      {e.orderNumber}
                    </Link>
                    <div className="truncate text-xs text-slate-500">{e.customerName}</div>
                  </div>
                </div>
                <OrderStatusBadge status={e.status} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

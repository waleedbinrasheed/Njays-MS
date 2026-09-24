import Link from "next/link";
import type { ReactNode } from "react";

interface StatTileProps {
  label: string;
  value: ReactNode;
  /** Supporting line under the value, e.g. "12 invoices". */
  detail?: ReactNode;
  /** Status tiles (low / out of stock) keep a text label as well as colour, never colour alone. */
  status?: { tone: "warning" | "critical"; text: string };
  href?: string;
  onClick?: () => void;
}

const STATUS_STYLE = {
  warning: "bg-amber-50 text-amber-800 ring-amber-200",
  critical: "bg-red-50 text-red-700 ring-red-200",
} as const;

/**
 * KPI tile: sentence-case label, semibold value in proportional figures (tabular figures are for
 * aligned columns only), optional detail and status chip.
 */
export function StatTile({ label, value, detail, status, href, onClick }: StatTileProps) {
  const body = (
    <>
      <p className="text-sm text-slate-600">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
      <div className="mt-1 flex min-h-5 flex-wrap items-center gap-2 text-xs text-slate-500">
        {status && (
          <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-medium ring-1 ${STATUS_STYLE[status.tone]}`}>
            <span aria-hidden>{status.tone === "critical" ? "●" : "▲"}</span>
            {status.text}
          </span>
        )}
        {detail}
      </div>
    </>
  );
  const base = "block rounded-lg border border-slate-200 bg-white px-4 py-3 text-left";
  if (href) {
    return (
      <Link href={href} className={`${base} hover:border-slate-300 hover:bg-slate-50`}>
        {body}
      </Link>
    );
  }
  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={`${base} hover:border-slate-300 hover:bg-slate-50`}>
        {body}
      </button>
    );
  }
  return <div className={base}>{body}</div>;
}

/** 1,284 · 12.9K · 4.2M */
export function compactNumber(value: number): string {
  return new Intl.NumberFormat("en-PK", { notation: value >= 100_000 ? "compact" : "standard", maximumFractionDigits: 1 }).format(value);
}

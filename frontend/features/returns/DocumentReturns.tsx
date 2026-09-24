"use client";

import Link from "next/link";
import { useApiQuery } from "@/hooks/useApiQuery";
import { returnServices } from "@/services/returnService";
import type { ReturnKind } from "@/types/returns";
import { formatCurrency, formatDate } from "@/utils/format";
import { RETURN_KINDS } from "./returnKinds";

/** Returns recorded against one invoice / purchase, shown under the document (screen only). */
export function useDocumentReturns(kind: ReturnKind, documentId: number, refreshKey: unknown) {
  return useApiQuery(
    (signal) => returnServices[kind].list({ documentId, size: 50, sort: "returnDate,asc" }, signal),
    [kind, documentId, refreshKey],
  );
}

export function DocumentReturnsList({ kind, returns }: { kind: ReturnKind; returns: { id: number; returnNumber: string; returnDate: string; total: number }[] }) {
  const cfg = RETURN_KINDS[kind];
  if (returns.length === 0) return null;
  return (
    <section className="mt-5 rounded-lg border border-slate-200 bg-white p-4 print:hidden">
      <h2 className="mb-2 text-sm font-semibold text-slate-900">Returns against this {cfg.documentLabel.toLowerCase()}</h2>
      <ul className="divide-y divide-slate-100 text-sm">
        {returns.map((r) => (
          <li key={r.id} className="flex items-center justify-between py-2">
            <Link href={cfg.detailHref(r.id)} className="font-mono text-xs font-medium text-blue-700 hover:underline">
              {r.returnNumber}
            </Link>
            <span className="text-slate-600">{formatDate(r.returnDate)}</span>
            <span className="tabular-nums">
              {cfg.amountLabel} {formatCurrency(r.total)}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

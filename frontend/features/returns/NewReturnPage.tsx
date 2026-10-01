"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { TextAreaField, TextField } from "@/components/ui/FormField";
import { PageSpinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useUnsavedChangesWarning } from "@/hooks/useUnsavedChangesWarning";
import { ApiError } from "@/services/apiClient";
import { purchaseService } from "@/services/purchaseService";
import { returnServices } from "@/services/returnService";
import { saleService } from "@/services/saleService";
import type { ReturnKind } from "@/types/returns";
import { formatCurrency, formatDate, todayIso } from "@/utils/format";
import { RETURN_KINDS, previewReturnAmount } from "./returnKinds";

interface DocumentHeader {
  number: string;
  date: string;
  party: string;
  cancelled: boolean;
}

async function loadHeader(kind: ReturnKind, id: number, signal: AbortSignal): Promise<DocumentHeader> {
  if (kind === "sale") {
    const s = await saleService.get(id, signal);
    const party = s.representative?.name ?? s.customer?.name ?? "-";
    return { number: s.invoiceNumber, date: s.saleDate, party, cancelled: s.status === "CANCELLED" };
  }
  const p = await purchaseService.get(id, signal);
  return { number: p.purchaseNumber, date: p.purchaseDate, party: p.supplier.name, cancelled: p.status === "CANCELLED" };
}

export function NewReturnPage({ kind, documentId }: { kind: ReturnKind; documentId: number }) {
  const cfg = RETURN_KINDS[kind];
  const service = returnServices[kind];
  const router = useRouter();
  const toast = useToast();

  const header = useApiQuery((signal) => loadHeader(kind, documentId, signal), [kind, documentId]);
  const lines = useApiQuery((signal) => service.returnable(documentId, signal), [kind, documentId]);

  const [quantities, setQuantities] = useState<Record<number, string>>({});
  const [date, setDate] = useState(todayIso());
  const [reason, setReason] = useState("");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  const qty = (itemId: number) => {
    const v = quantities[itemId] ?? "";
    return v.trim() === "" ? 0 : Number(v);
  };
  const chosen = (lines.data ?? []).filter((l) => qty(l.documentItemId) > 0);
  const total = chosen.reduce((sum, l) => sum + previewReturnAmount(l, qty(l.documentItemId)), 0);
  const units = chosen.reduce((n, l) => n + qty(l.documentItemId), 0);

  useUnsavedChangesWarning(chosen.length > 0 && !saving);

  if (header.error || lines.error) return <ErrorBanner message={header.error ?? lines.error ?? ""} />;
  if (!header.data || !lines.data) return <PageSpinner />;
  const doc = header.data;
  const anyReturnable = lines.data.some((l) => l.returnableQuantity > 0);

  function review(e: FormEvent) {
    e.preventDefault();
    const local: Record<string, string> = {};
    for (const l of lines.data ?? []) {
      const q = qty(l.documentItemId);
      if (!Number.isInteger(q) || q < 0) local[`q${l.documentItemId}`] = "Whole number";
      else if (q > l.returnableQuantity) local[`q${l.documentItemId}`] = `Max ${l.returnableQuantity}`;
    }
    if (chosen.length === 0) local.lines = "Enter a quantity for at least one item";
    if (!reason.trim()) local.reason = "Give a reason for the return";
    if (!date) local.date = "Return date is required";
    else if (date < doc.date) local.date = `Cannot be before the ${cfg.documentLabel.toLowerCase()} date`;
    else if (date > todayIso()) local.date = "Cannot be in the future";
    setErrors(local);
    setFormError(null);
    if (Object.keys(local).length === 0) setConfirming(true);
  }

  async function save() {
    setSaving(true);
    try {
      const saved = await service.create({
        documentId,
        returnDate: date,
        reason,
        notes,
        items: chosen.map((l) => ({ documentItemId: l.documentItemId, quantity: qty(l.documentItemId) })),
      });
      toast.success(`Return ${saved.returnNumber} recorded`);
      router.push(cfg.detailHref(saved.id));
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not record the return");
      setSaving(false);
      setConfirming(false);
      lines.reload();
    }
  }

  return (
    <>
      <PageHeader
        title={`Return items: ${doc.number}`}
        description={`${cfg.partyLabel}: ${doc.party} · ${formatDate(doc.date)}. Returned items are ${cfg.stockEffect}.`}
      />
      <Link href={cfg.documentHref(documentId)} className="mb-4 inline-block text-sm text-slate-600 hover:text-slate-900">
        ← Back to {cfg.documentLabel.toLowerCase()}
      </Link>

      {doc.cancelled || !anyReturnable ? (
        <EmptyState
          title="Nothing left to return"
          description={doc.cancelled ? `This ${cfg.documentLabel.toLowerCase()} is cancelled.` : "Every item has already been returned."}
        />
      ) : (
        <form onSubmit={review} className="space-y-5" noValidate>
          {formError && (
            <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {formError}
            </p>
          )}
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-2.5 font-medium">Item</th>
                  <th className="px-4 py-2.5 text-right font-medium">On {cfg.documentLabel.toLowerCase()}</th>
                  <th className="px-4 py-2.5 text-right font-medium">Already returned</th>
                  <th className="w-32 px-4 py-2.5 text-right font-medium">Return now</th>
                  <th className="px-4 py-2.5 text-right font-medium">{cfg.amountLabel}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {lines.data.map((l) => {
                  const error = errors[`q${l.documentItemId}`];
                  const q = qty(l.documentItemId);
                  return (
                    <tr key={l.documentItemId} className={l.returnableQuantity === 0 ? "text-slate-400" : ""}>
                      <td className="px-4 py-2.5">
                        <div className="font-medium">{l.product.title}</div>
                        <div className="text-xs text-slate-500">{l.product.isbn ?? "No ISBN"}</div>
                      </td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{l.originalQuantity}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{l.returnedQuantity}</td>
                      <td className="px-4 py-2.5">
                        <input
                          aria-label={`Quantity to return of ${l.product.title}`}
                          type="number"
                          inputMode="numeric"
                          min="0"
                          max={l.returnableQuantity}
                          step="1"
                          placeholder="0"
                          disabled={l.returnableQuantity === 0}
                          value={quantities[l.documentItemId] ?? ""}
                          onChange={(e) => setQuantities((m) => ({ ...m, [l.documentItemId]: e.target.value }))}
                          className={`w-full rounded border px-2 py-1 text-right tabular-nums disabled:bg-slate-100 ${error ? "border-red-400" : "border-slate-300"}`}
                        />
                        {error && <div className="mt-0.5 text-right text-xs text-red-600">{error}</div>}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                        {q > 0 && q <= l.returnableQuantity ? formatCurrency(previewReturnAmount(l, q)) : "-"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {errors.lines && <p className="text-sm text-red-600">{errors.lines}</p>}

          <div className="grid gap-4 lg:grid-cols-[1fr_20rem]">
            <div className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2">
              <TextField label="Return date" type="date" min={doc.date} max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} error={errors.date} required />
              <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} error={errors.reason} placeholder="e.g. damaged, wrong book" required />
              <div className="sm:col-span-2">
                <TextAreaField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
              </div>
            </div>
            <div className="h-fit space-y-3 rounded-lg border border-slate-200 bg-white p-4">
              <div className="flex justify-between text-sm text-slate-600">
                <span>Units returned</span>
                <span className="tabular-nums">{units}</span>
              </div>
              <div className="flex justify-between text-base font-semibold text-slate-900">
                <span>{cfg.amountLabel}</span>
                <span className="tabular-nums">{formatCurrency(total)}</span>
              </div>
              <p className="text-xs text-slate-400">Based on each line&apos;s total after its discount and tax.</p>
              <Button type="submit" className="w-full" disabled={chosen.length === 0}>
                Review return
              </Button>
            </div>
          </div>
        </form>
      )}

      <ConfirmDialog
        open={confirming}
        title="Record return?"
        message={
          <div className="space-y-1">
            <p>
              {units} unit(s) from <strong>{doc.number}</strong> will be {cfg.stockEffect}.
            </p>
            <p className="text-base font-semibold text-slate-900">
              {cfg.amountLabel} {formatCurrency(total)}
            </p>
            <p className="text-xs text-slate-500">Returns are permanent and cannot be edited or undone.</p>
          </div>
        }
        confirmLabel="Record return"
        loading={saving}
        onConfirm={() => void save()}
        onCancel={() => setConfirming(false)}
      />
    </>
  );
}

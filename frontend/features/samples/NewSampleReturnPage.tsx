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
import { sampleService } from "@/services/sampleService";
import { formatCurrency, formatDate, todayIso } from "@/utils/format";

/** Record books a representative brought back. Each is valued at its original sample price. */
export function NewSampleReturnPage({ sampleId }: { sampleId: number }) {
  const router = useRouter();
  const toast = useToast();
  const sample = useApiQuery((signal) => sampleService.get(sampleId, signal), [sampleId]);

  const [quantities, setQuantities] = useState<Record<number, string>>({});
  const [date, setDate] = useState(todayIso());
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  const qty = (itemId: number) => {
    const v = quantities[itemId] ?? "";
    return v.trim() === "" ? 0 : Number(v);
  };
  const items = sample.data?.items ?? [];
  const chosen = items.filter((l) => qty(l.id) > 0);
  const total = chosen.reduce((sum, l) => sum + Math.round(qty(l.id) * l.unitPrice * 100) / 100, 0);
  const units = chosen.reduce((n, l) => n + qty(l.id), 0);

  useUnsavedChangesWarning(chosen.length > 0 && !saving);

  if (sample.error) return <ErrorBanner message={sample.error} onRetry={sample.reload} />;
  if (!sample.data) return <PageSpinner />;
  const s = sample.data;
  const cancelled = s.status === "CANCELLED";

  function returnAll() {
    setQuantities(Object.fromEntries(items.filter((l) => l.outstandingQuantity > 0).map((l) => [l.id, String(l.outstandingQuantity)])));
  }

  function review(e: FormEvent) {
    e.preventDefault();
    const local: Record<string, string> = {};
    for (const l of items) {
      const q = qty(l.id);
      if (!Number.isInteger(q) || q < 0) local[`q${l.id}`] = "Whole number";
      else if (q > l.outstandingQuantity) local[`q${l.id}`] = `Max ${l.outstandingQuantity}`;
    }
    if (chosen.length === 0) local.lines = "Enter a quantity for at least one book";
    if (!date) local.date = "Return date is required";
    else if (date < s.issueDate) local.date = "Cannot be before the issue date";
    else if (date > todayIso()) local.date = "Cannot be in the future";
    setErrors(local);
    setFormError(null);
    if (Object.keys(local).length === 0) setConfirming(true);
  }

  async function save() {
    setSaving(true);
    try {
      const saved = await sampleService.createReturn({
        sampleId,
        returnDate: date,
        notes,
        items: chosen.map((l) => ({ sampleItemId: l.id, quantity: qty(l.id) })),
      });
      toast.success(`Sample return ${saved.returnNumber} recorded`);
      router.push(`/samples/${sampleId}`);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not record the return");
      setSaving(false);
      setConfirming(false);
      sample.reload();
    }
  }

  return (
    <>
      <PageHeader
        title={`Return samples: ${s.sampleNumber}`}
        description={`Representative: ${s.representative.name}${s.customer ? ` (for ${s.customer.name})` : ""} · issued ${formatDate(s.issueDate)}. Returned books go back into stock.`}
      />
      <Link href={`/samples/${sampleId}`} className="mb-4 inline-block text-sm text-slate-600 hover:text-slate-900">
        ← Back to sample
      </Link>

      {cancelled || s.outstandingQuantity === 0 ? (
        <EmptyState
          title="Nothing left to return"
          description={cancelled ? "This sample is cancelled." : "Every sample has already been returned."}
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
                  <th className="px-4 py-2.5 font-medium">Book</th>
                  <th className="px-4 py-2.5 text-right font-medium">Sample price</th>
                  <th className="px-4 py-2.5 text-right font-medium">Issued</th>
                  <th className="px-4 py-2.5 text-right font-medium">Returned</th>
                  <th className="px-4 py-2.5 text-right font-medium">Outstanding</th>
                  <th className="w-32 px-4 py-2.5 text-right font-medium">Return now</th>
                  <th className="px-4 py-2.5 text-right font-medium">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((l) => {
                  const error = errors[`q${l.id}`];
                  const q = qty(l.id);
                  return (
                    <tr key={l.id} className={l.outstandingQuantity === 0 ? "text-slate-400" : ""}>
                      <td className="px-4 py-2.5">
                        <div className="font-medium">{l.product.title}</div>
                        <div className="text-xs text-slate-500">{l.product.isbn ?? "No ISBN"}</div>
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{formatCurrency(l.unitPrice)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{l.quantity}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{l.returnedQuantity}</td>
                      <td className="px-4 py-2.5 text-right font-medium tabular-nums">{l.outstandingQuantity}</td>
                      <td className="px-4 py-2.5">
                        <input
                          aria-label={`Quantity to return of ${l.product.title}`}
                          type="number"
                          inputMode="numeric"
                          min="0"
                          max={l.outstandingQuantity}
                          step="1"
                          placeholder="0"
                          disabled={l.outstandingQuantity === 0}
                          value={quantities[l.id] ?? ""}
                          onChange={(e) => setQuantities((m) => ({ ...m, [l.id]: e.target.value }))}
                          className={`w-full rounded border px-2 py-1 text-right tabular-nums disabled:bg-slate-100 ${error ? "border-red-400" : "border-slate-300"}`}
                        />
                        {error && <div className="mt-0.5 text-right text-xs text-red-600">{error}</div>}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                        {q > 0 && q <= l.outstandingQuantity ? formatCurrency(q * l.unitPrice) : "-"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between">
            {errors.lines ? <p className="text-sm text-red-600">{errors.lines}</p> : <span />}
            <Button variant="secondary" size="sm" onClick={returnAll}>
              Return everything outstanding
            </Button>
          </div>

          <div className="grid gap-4 lg:grid-cols-[1fr_20rem]">
            <div className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2">
              <TextField label="Return date" type="date" min={s.issueDate} max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} error={errors.date} required />
              <div className="sm:col-span-2">
                <TextAreaField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} maxLength={500} />
              </div>
            </div>
            <div className="h-fit space-y-3 rounded-lg border border-slate-200 bg-white p-4">
              <div className="flex justify-between text-sm text-slate-600">
                <span>Units returned</span>
                <span className="tabular-nums">{units}</span>
              </div>
              <div className="flex justify-between text-base font-semibold text-slate-900">
                <span>Return amount</span>
                <span className="tabular-nums">{formatCurrency(total)}</span>
              </div>
              <p className="text-xs text-slate-400">Quantity × the original sample price.</p>
              <Button type="submit" className="w-full" disabled={chosen.length === 0}>
                Review and save
              </Button>
            </div>
          </div>
        </form>
      )}

      <ConfirmDialog
        open={confirming}
        title="Record sample return?"
        message={
          <div className="space-y-1">
            <p>
              {units} unit(s) from <strong>{s.representative.name}</strong> will go back into stock.
            </p>
            <p className="text-base font-semibold text-slate-900">Return amount {formatCurrency(total)}</p>
            <p className="text-xs text-slate-500">Saved returns cannot be edited.</p>
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

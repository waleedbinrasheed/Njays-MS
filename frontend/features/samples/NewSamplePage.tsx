"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { PageHeader } from "@/components/ui/Feedback";
import { TextAreaField, TextField } from "@/components/ui/FormField";
import { useToast } from "@/components/ui/Toast";
import { LineItemsEditor, num, type DraftLine } from "@/features/documents/LineItemsEditor";
import { loadSampleRepresentativeOptions } from "@/features/master/lookups";
import { useUnsavedChangesWarning } from "@/hooks/useUnsavedChangesWarning";
import { ApiError } from "@/services/apiClient";
import { sampleService } from "@/services/sampleService";
import { formatCurrency, todayIso } from "@/utils/format";
import { SampleSaleBanner } from "./SampleStatusBadge";

const lineAmount = (l: DraftLine) => Math.round(num(l.quantity) * num(l.unitPrice) * 100) / 100;

export function NewSamplePage() {
  const router = useRouter();
  const toast = useToast();
  const [representative, setRepresentative] = useState<Option | null>(null);
  const [date, setDate] = useState(todayIso());
  const [notes, setNotes] = useState("");
  const [lines, setLines] = useState<DraftLine[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  useUnsavedChangesWarning(lines.length > 0 && !saving);

  const total = lines.reduce((sum, l) => sum + lineAmount(l), 0);
  const units = lines.reduce((n, l) => n + num(l.quantity), 0);

  function review(e: FormEvent) {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!representative) local.representative = "Choose the representative taking the samples";
    if (!date) local.date = "Issue date is required";
    else if (date > todayIso()) local.date = "Issue date cannot be in the future";
    if (lines.length === 0) local.lines = "Add at least one book";
    else if (lines.some((l) => !Number.isInteger(num(l.quantity)) || num(l.quantity) < 1))
      local.lines = "Every quantity must be a whole number of at least 1";
    else if (lines.some((l) => num(l.quantity) > l.stock)) local.lines = "Some quantities are more than the stock available";
    else if (lines.some((l) => l.unitPrice.trim() === "" || num(l.unitPrice) < 0)) local.lines = "Every line needs a sample price (0 or more)";
    else if (lines.some((l) => l.sellingPrice !== undefined && num(l.unitPrice) > l.sellingPrice))
      local.lines = "A sample price cannot be above the book's selling price";
    setErrors(local);
    setFormError(null);
    if (Object.keys(local).length === 0) setConfirming(true);
  }

  async function save() {
    if (!representative) return;
    setSaving(true);
    try {
      const saved = await sampleService.create({
        representativeId: representative.id,
        issueDate: date,
        notes,
        items: lines.map((l) => ({ productId: l.productId, quantity: num(l.quantity), unitPrice: num(l.unitPrice) })),
      });
      toast.success(`Sample ${saved.sampleNumber} issued`);
      router.push(`/samples/${saved.id}`);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not save the sample. Please try again.");
      setSaving(false);
      setConfirming(false);
    }
  }

  return (
    <>
      <PageHeader
        title="New Sample Issue"
        description="Give books to a representative as samples. Stock is reduced when you save; this is not a sale."
      />

      <form onSubmit={review} className="space-y-5" noValidate>
        <SampleSaleBanner />
        {formError && (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {formError}
          </p>
        )}

        <section className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-4">
          <div className="sm:col-span-2">
            <AsyncSelect
              label="Representative"
              value={representative}
              onChange={setRepresentative}
              load={loadSampleRepresentativeOptions}
              error={errors.representative}
              placeholder="Search representatives"
              required
            />
          </div>
          <TextField label="Issue date" type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} error={errors.date} required />
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-4">
          <LineItemsEditor
            lines={lines}
            onChange={setLines}
            priceLabel="Sample price"
            defaultPrice={(i) => i.purchasePrice}
            showStockWarning
            simple
          />
          <p className="mt-2 text-xs text-slate-500">
            The sample price starts at the book&apos;s purchase price (actual cost) and can be changed, but not above its selling price.
          </p>
          {errors.lines && <p className="mt-2 text-sm text-red-600">{errors.lines}</p>}
        </section>

        <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
          <TextAreaField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} rows={4} />
          <div className="space-y-3">
            <div className="space-y-2 rounded-lg border border-slate-200 bg-white p-4">
              <div className="flex justify-between text-sm text-slate-600">
                <span>Books / units</span>
                <span className="tabular-nums">
                  {lines.length} / {units}
                </span>
              </div>
              <div className="flex justify-between text-base font-semibold text-slate-900">
                <span>Sample value</span>
                <span className="tabular-nums">{formatCurrency(total)}</span>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => router.push("/samples")} disabled={saving}>
                Cancel
              </Button>
              <Button type="submit" disabled={lines.length === 0}>
                Review and save
              </Button>
            </div>
          </div>
        </div>
      </form>

      <ConfirmDialog
        open={confirming}
        title="Issue samples?"
        message={
          <div className="space-y-1">
            <p>
              To <strong>{representative?.label}</strong> · {date}
            </p>
            <p>
              {lines.length} book(s), {units} unit(s) will be taken from stock.
            </p>
            <p className="text-base font-semibold text-slate-900">Sample value {formatCurrency(total)}</p>
            <p className="text-xs text-slate-500">Recorded as a SAMPLE SALE, not a retail sale. Saved samples cannot be edited.</p>
          </div>
        }
        confirmLabel="Issue samples"
        loading={saving}
        onConfirm={() => void save()}
        onCancel={() => setConfirming(false)}
      />
    </>
  );
}

"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { PageHeader } from "@/components/ui/Feedback";
import { SelectField, TextAreaField, TextField } from "@/components/ui/FormField";
import { useToast } from "@/components/ui/Toast";
import { LineItemsEditor, num, toPricedLine, type DraftLine } from "@/features/documents/LineItemsEditor";
import { TotalsPanel } from "@/features/documents/TotalsPanel";
import { loadSupplierOptions } from "@/features/master/lookups";
import { useUnsavedChangesWarning } from "@/hooks/useUnsavedChangesWarning";
import { ApiError } from "@/services/apiClient";
import { purchaseService } from "@/services/purchaseService";
import type { PaymentStatus } from "@/types/purchase";
import { previewTotals } from "@/utils/documentTotals";
import { formatCurrency, todayIso } from "@/utils/format";
import { PAYMENT_STATUS_LABELS } from "@/utils/labels";

export function NewPurchasePage() {
  const router = useRouter();
  const toast = useToast();
  const [supplier, setSupplier] = useState<Option | null>(null);
  const [date, setDate] = useState(todayIso());
  const [supplierInvoice, setSupplierInvoice] = useState("");
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>("UNPAID");
  const [notes, setNotes] = useState("");
  const [lines, setLines] = useState<DraftLine[]>([]);
  const [invoiceDiscount, setInvoiceDiscount] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  useUnsavedChangesWarning(lines.length > 0 && !saving);

  const totals = previewTotals(lines.map(toPricedLine), num(invoiceDiscount));
  const units = lines.reduce((n, l) => n + num(l.quantity), 0);

  function review(e: FormEvent) {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!supplier) local.supplier = "Choose a supplier";
    if (!date) local.date = "Purchase date is required";
    else if (date > todayIso()) local.date = "Purchase date cannot be in the future";
    if (lines.length === 0) local.lines = "Add at least one product";
    if (lines.some((l) => !Number.isInteger(num(l.quantity)) || num(l.quantity) < 1)) local.lines = "Every quantity must be a whole number of at least 1";
    if (lines.some((l) => l.unitPrice.trim() === "" || num(l.unitPrice) < 0)) local.lines = "Every line needs a unit cost of 0 or more";
    if (lines.some((l) => num(l.discountPercent) < 0 || num(l.discountPercent) > 100 || num(l.taxPercent) < 0 || num(l.taxPercent) > 100))
      local.lines = "Discount and tax must be between 0 and 100%";
    if (totals.invoiceDiscountTooLarge) local.lines = "Invoice discount is larger than the amount due";
    setErrors(local);
    setFormError(null);
    if (Object.keys(local).length === 0) setConfirming(true);
  }

  async function save() {
    if (!supplier) return;
    setSaving(true);
    try {
      const saved = await purchaseService.create({
        supplierId: supplier.id,
        purchaseDate: date,
        supplierInvoiceNumber: supplierInvoice,
        paymentStatus,
        invoiceDiscount: num(invoiceDiscount),
        notes,
        items: lines.map((l) => ({
          productId: l.productId,
          quantity: num(l.quantity),
          unitCost: num(l.unitPrice),
          discountPercent: num(l.discountPercent),
          taxPercent: num(l.taxPercent),
        })),
      });
      toast.success(`Purchase ${saved.purchaseNumber} saved. Stock updated.`);
      router.push(`/purchases/${saved.id}`);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not save the purchase. Please try again.");
      setSaving(false);
      setConfirming(false);
    }
  }

  return (
    <>
      <PageHeader title="New Purchase" description="Record goods received from a supplier. Stock increases when you save." />

      <form onSubmit={review} className="space-y-5" noValidate>
        {formError && (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {formError}
          </p>
        )}

        <section className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-4">
          <AsyncSelect label="Supplier" value={supplier} onChange={setSupplier} load={loadSupplierOptions} error={errors.supplier} required />
          <TextField label="Purchase date" type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} error={errors.date} required />
          <TextField label="Supplier's invoice no." value={supplierInvoice} onChange={(e) => setSupplierInvoice(e.target.value)} maxLength={50} />
          <SelectField label="Payment status" value={paymentStatus} onChange={(e) => setPaymentStatus(e.target.value as PaymentStatus)} required>
            {Object.entries(PAYMENT_STATUS_LABELS).map(([k, label]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </SelectField>
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-4">
          <LineItemsEditor lines={lines} onChange={setLines} priceLabel="Unit cost" defaultPrice={(i) => i.purchasePrice} />
          {errors.lines && <p className="mt-2 text-sm text-red-600">{errors.lines}</p>}
        </section>

        <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
          <TextAreaField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} rows={4} />
          <div className="space-y-3">
            <TotalsPanel totals={totals} invoiceDiscount={invoiceDiscount} onInvoiceDiscountChange={setInvoiceDiscount} />
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => router.push("/purchases")} disabled={saving}>
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
        title="Save purchase?"
        message={
          <div className="space-y-1">
            <p>
              <strong>{supplier?.label}</strong> · {date}
            </p>
            <p>
              {lines.length} product(s), {units} unit(s) will be added to stock.
            </p>
            <p className="text-base font-semibold text-slate-900">Total {formatCurrency(totals.total)}</p>
            <p className="text-xs text-slate-500">Saved purchases cannot be edited; they can only be cancelled.</p>
          </div>
        }
        confirmLabel="Save purchase"
        loading={saving}
        onConfirm={() => void save()}
        onCancel={() => setConfirming(false)}
      />
    </>
  );
}

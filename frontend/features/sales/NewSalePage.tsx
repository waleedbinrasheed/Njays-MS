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
import { loadCustomerOptions } from "@/features/master/lookups";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useUnsavedChangesWarning } from "@/hooks/useUnsavedChangesWarning";
import { ApiError } from "@/services/apiClient";
import { customerService } from "@/services/masterDataService";
import { saleService } from "@/services/saleService";
import type { PaymentStatus } from "@/types/purchase";
import { previewTotals } from "@/utils/documentTotals";
import { formatCurrency, todayIso } from "@/utils/format";
import { PAYMENT_STATUS_LABELS } from "@/utils/labels";

export function NewSalePage() {
  const router = useRouter();
  const toast = useToast();
  const walkIn = useApiQuery(() => customerService.walkIn(), []);
  const [chosenCustomer, setChosenCustomer] = useState<Option | null | undefined>(undefined); // undefined = default
  const [date, setDate] = useState(todayIso());
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>("PAID");
  const [notes, setNotes] = useState("");
  const [lines, setLines] = useState<DraftLine[]>([]);
  const [invoiceDiscount, setInvoiceDiscount] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  useUnsavedChangesWarning(lines.length > 0 && !saving);

  const walkInOption = walkIn.data ? { id: walkIn.data.id, label: walkIn.data.name } : null;
  const customer = chosenCustomer === undefined ? walkInOption : chosenCustomer;

  const totals = previewTotals(lines.map(toPricedLine), num(invoiceDiscount));
  const units = lines.reduce((n, l) => n + num(l.quantity), 0);

  function review(e: FormEvent) {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!customer) local.customer = "Choose a customer";
    if (!date) local.date = "Sale date is required";
    else if (date > todayIso()) local.date = "Sale date cannot be in the future";
    if (lines.length === 0) local.lines = "Add at least one product";
    else if (lines.some((l) => !Number.isInteger(num(l.quantity)) || num(l.quantity) < 1))
      local.lines = "Every quantity must be a whole number of at least 1";
    else if (lines.some((l) => num(l.quantity) > l.stock)) local.lines = "Some quantities are more than the stock available";
    else if (lines.some((l) => num(l.unitPrice) <= 0)) local.lines = "Every line needs a unit price above zero";
    else if (lines.some((l) => num(l.discountPercent) < 0 || num(l.discountPercent) > 100 || num(l.taxPercent) < 0 || num(l.taxPercent) > 100))
      local.lines = "Discount and tax must be between 0 and 100%";
    else if (totals.invoiceDiscountTooLarge) local.lines = "Invoice discount is larger than the amount due";
    setErrors(local);
    setFormError(null);
    if (Object.keys(local).length === 0) setConfirming(true);
  }

  async function save() {
    if (!customer) return;
    setSaving(true);
    try {
      const saved = await saleService.create({
        customerId: customer.id,
        saleDate: date,
        paymentStatus,
        invoiceDiscount: num(invoiceDiscount),
        notes,
        items: lines.map((l) => ({
          productId: l.productId,
          quantity: num(l.quantity),
          unitPrice: num(l.unitPrice),
          discountPercent: num(l.discountPercent),
          taxPercent: num(l.taxPercent),
        })),
      });
      toast.success(`Invoice ${saved.invoiceNumber} saved`);
      router.push(`/sales/${saved.id}`);
    } catch (err) {
      // e.g. "Insufficient stock for 'X': available 1, requested 2" if someone else sold it meanwhile.
      setFormError(err instanceof ApiError ? err.message : "Could not save the sale. Please try again.");
      setSaving(false);
      setConfirming(false);
    }
  }

  return (
    <>
      <PageHeader title="New Sale" description="Create a sales invoice. Stock is reduced when you save." />

      <form onSubmit={review} className="space-y-5" noValidate>
        {formError && (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {formError}
          </p>
        )}

        <section className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-3">
          <AsyncSelect
            label="Customer"
            value={customer}
            onChange={setChosenCustomer}
            load={loadCustomerOptions}
            error={errors.customer}
            placeholder="Walk-in Customer"
            required
          />
          <TextField label="Sale date" type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} error={errors.date} required />
          <SelectField label="Payment status" value={paymentStatus} onChange={(e) => setPaymentStatus(e.target.value as PaymentStatus)} required>
            {Object.entries(PAYMENT_STATUS_LABELS).map(([k, label]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </SelectField>
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-4">
          <LineItemsEditor lines={lines} onChange={setLines} priceLabel="Unit price" defaultPrice={(i) => i.sellingPrice} showStockWarning />
          {errors.lines && <p className="mt-2 text-sm text-red-600">{errors.lines}</p>}
        </section>

        <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
          <TextAreaField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} rows={4} />
          <div className="space-y-3">
            <TotalsPanel totals={totals} invoiceDiscount={invoiceDiscount} onInvoiceDiscountChange={setInvoiceDiscount} />
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => router.push("/sales")} disabled={saving}>
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
        title="Save sale?"
        message={
          <div className="space-y-1">
            <p>
              <strong>{customer?.label}</strong> · {date} · {PAYMENT_STATUS_LABELS[paymentStatus]}
            </p>
            <p>
              {lines.length} product(s), {units} unit(s) will be taken from stock.
            </p>
            <p className="text-base font-semibold text-slate-900">Total {formatCurrency(totals.total)}</p>
            <p className="text-xs text-slate-500">Saved invoices cannot be edited; they can only be cancelled.</p>
          </div>
        }
        confirmLabel="Save sale"
        loading={saving}
        onConfirm={() => void save()}
        onCancel={() => setConfirming(false)}
      />
    </>
  );
}

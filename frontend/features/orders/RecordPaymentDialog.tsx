"use client";

import { useState, type FormEvent } from "react";
import { SelectField, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { ApiError } from "@/services/apiClient";
import { orderService } from "@/services/orderService";
import type { Order, PaymentKind, PaymentMethod } from "@/types/order";
import { formatCurrency, todayIso } from "@/utils/format";
import { PAYMENT_KIND_LABELS, PAYMENT_METHOD_LABELS } from "@/utils/labels";

const DECIMAL_2 = /^\d+(\.\d{1,2})?$/;

interface RecordPaymentDialogProps {
  order: Order;
  onClose: () => void;
  onSaved: () => void;
}

/** Records an advance or a later payment. The amount may not exceed the balance still due. */
export function RecordPaymentDialog({ order, onClose, onSaved }: RecordPaymentDialogProps) {
  const [kind, setKind] = useState<PaymentKind>(order.payments.length === 0 ? "ADVANCE" : "PAYMENT");
  const [amount, setAmount] = useState("");
  const [paidOn, setPaidOn] = useState(todayIso());
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [note, setNote] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const value = Number(amount);
    const local: Record<string, string> = {};
    if (!DECIMAL_2.test(amount.trim()) || value <= 0) local.amount = "Enter an amount above zero (up to 2 decimals)";
    else if (value > order.balance + 1e-9) local.amount = `The balance due is ${formatCurrency(order.balance)}`;
    if (!paidOn) local.paidOn = "Date is required";
    else if (paidOn > todayIso()) local.paidOn = "Date cannot be in the future";
    setErrors(local);
    setFormError(null);
    if (Object.keys(local).length) return;

    setSaving(true);
    try {
      await orderService.recordPayment(order.id, { kind, amount: value, paidOn, method, note: note.trim() });
      onSaved();
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setErrors(err.fieldErrors);
      else setFormError(err instanceof ApiError ? err.message : "Could not record the payment");
      setSaving(false);
    }
  }

  return (
    <FormDialog
      title={`Record payment · ${order.orderNumber}`}
      submitLabel="Record payment"
      saving={saving}
      formError={formError}
      onSubmit={submit}
      onClose={onClose}
    >
      <p className="text-sm text-slate-600">
        Balance due: <strong className="tabular-nums text-slate-900">{formatCurrency(order.balance)}</strong>
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Kind" value={kind} onChange={(e) => setKind(e.target.value as PaymentKind)} required>
          {(Object.keys(PAYMENT_KIND_LABELS) as PaymentKind[]).map((k) => (
            <option key={k} value={k}>
              {PAYMENT_KIND_LABELS[k]}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Amount (PKR)"
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          error={errors.amount}
          required
          autoFocus
        />
        <TextField label="Date paid" type="date" value={paidOn} max={todayIso()} onChange={(e) => setPaidOn(e.target.value)} error={errors.paidOn} required />
        <SelectField label="Method" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)} required>
          {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((m) => (
            <option key={m} value={m}>
              {PAYMENT_METHOD_LABELS[m]}
            </option>
          ))}
        </SelectField>
      </div>
      <TextField label="Note" value={note} onChange={(e) => setNote(e.target.value)} error={errors.note} />
    </FormDialog>
  );
}

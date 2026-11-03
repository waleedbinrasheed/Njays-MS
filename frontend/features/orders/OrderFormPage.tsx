"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { TextAreaField, TextField } from "@/components/ui/FormField";
import { PageSpinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { CustomerFormDialog } from "@/features/master/CustomerFormDialog";
import { loadCustomerOptions } from "@/features/master/lookups";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useUnsavedChangesWarning } from "@/hooks/useUnsavedChangesWarning";
import { ApiError } from "@/services/apiClient";
import { measurementService } from "@/services/masterDataService";
import { orderService } from "@/services/orderService";
import type { Order, OrderInput } from "@/types/order";
import { lineAmounts, orderTotal } from "@/utils/orderTotals";
import { formatCurrency, todayIso } from "@/utils/format";
import { EDITABLE_STATUSES, ORDER_STATUS_LABELS } from "@/utils/labels";
import { OrderLineEditor } from "./OrderLineEditor";
import { emptyLine, lineFromSaved, num, toLineInput, type LineDraft } from "./orderLineDraft";

type LineErrors = Partial<Record<"garmentType" | "fabric" | "fabricMetres" | "stitchingCharge", string>>;

const DECIMAL_3 = /^\d+(\.\d{1,3})?$/;

/** Create (no order given) or edit one order. Edit is offered only while the order is still in production. */
export function OrderFormPage({ orderId }: { orderId?: number }) {
  const { hasPermission } = useAuth();
  const existing = useApiQuery((signal) => (orderId ? orderService.get(orderId, signal) : Promise.resolve(null)), [orderId]);

  if (orderId === undefined) return <OrderForm key="new" order={null} canAddCustomer={hasPermission("MASTER_DATA_MANAGE")} />;
  if (existing.error && !existing.data) return <ErrorBanner message={existing.error} onRetry={existing.reload} />;
  if (!existing.data) return <PageSpinner />;

  const order = existing.data;
  if (!EDITABLE_STATUSES.includes(order.status)) {
    return (
      <EmptyState
        title="This order can no longer be edited"
        description={`Its status is ${ORDER_STATUS_LABELS[order.status]}.`}
        action={
          <Link href={`/orders/${order.id}`} className="text-sm font-medium text-blue-700 hover:underline">
            Back to the order
          </Link>
        }
      />
    );
  }
  return <OrderForm key={order.id} order={order} canAddCustomer={hasPermission("MASTER_DATA_MANAGE")} />;
}

function OrderForm({ order, canAddCustomer }: { order: Order | null; canAddCustomer: boolean }) {
  const router = useRouter();
  const toast = useToast();

  const [customer, setCustomer] = useState<Option | null>(
    order ? { id: order.customer.id, label: order.customer.name, detail: order.customer.phone ?? undefined } : null,
  );
  const [orderDate, setOrderDate] = useState(order?.orderDate ?? todayIso());
  const [dueDate, setDueDate] = useState(order?.dueDate ?? "");
  const [trialDate, setTrialDate] = useState(order?.trialDate ?? "");
  const [deliveryDate, setDeliveryDate] = useState(order?.deliveryDate ?? "");
  const [notes, setNotes] = useState(order?.notes ?? "");
  const [lines, setLines] = useState<LineDraft[]>(() => (order ? order.lines.map(lineFromSaved) : [emptyLine()]));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [lineErrors, setLineErrors] = useState<Record<number, LineErrors>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [addingCustomer, setAddingCustomer] = useState(false);

  const records = useApiQuery(
    (signal) => (customer ? measurementService.forCustomer(customer.id, signal) : Promise.resolve([])),
    [customer?.id ?? null],
  );

  useUnsavedChangesWarning(!saving && (customer !== null || notes !== "" || lines.some((l) => l.garmentType !== null)));

  const amounts = lines.map((l) =>
    lineAmounts({
      fabricFromStock: l.fabricSource === "STOCK",
      fabricMetres: num(l.fabricMetres),
      fabricPricePerMetre: l.fabricPricePerMetre,
      stitchingCharge: num(l.stitchingCharge),
    }),
  );
  const total = orderTotal(amounts.map((a) => ({ lineTotal: a.lineTotal })));

  function updateLine(key: number, patch: Partial<LineDraft>) {
    setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  }

  function chooseCustomer(o: Option | null) {
    setCustomer(o);
    // Measurement records belong to one customer, so any choice made for the previous one is cleared.
    setLines((ls) => ls.map((l) => ({ ...l, measurementRecordId: null })));
  }

  function validate(): boolean {
    const local: Record<string, string> = {};
    const perLine: Record<number, LineErrors> = {};
    if (!customer) local.customer = "Choose the customer";
    if (!orderDate) local.orderDate = "Order date is required";
    if (!dueDate) local.dueDate = "Due date is required";
    else if (orderDate && dueDate < orderDate) local.dueDate = "Due date cannot be before the order date";
    if (lines.length === 0) local.lines = "Add at least one garment";
    for (const l of lines) {
      const e: LineErrors = {};
      if (!l.garmentType) e.garmentType = "Choose the garment type";
      if (l.fabricSource === "STOCK") {
        if (!l.fabric) e.fabric = "Choose the fabric from stock";
        if (!DECIMAL_3.test(l.fabricMetres.trim()) || num(l.fabricMetres) <= 0) e.fabricMetres = "Enter metres above zero, up to 3 decimals";
      }
      if (l.stitchingCharge.trim() !== "" && (!Number.isFinite(num(l.stitchingCharge)) || num(l.stitchingCharge) < 0))
        e.stitchingCharge = "Enter zero or a positive amount";
      if (Object.keys(e).length) perLine[l.key] = e;
    }
    setErrors(local);
    setLineErrors(perLine);
    setFormError(null);
    return Object.keys(local).length === 0 && Object.keys(perLine).length === 0;
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!validate() || !customer) return;
    const input: OrderInput = {
      customerId: customer.id,
      orderDate,
      dueDate,
      trialDate: trialDate || null,
      deliveryDate: deliveryDate || null,
      notes: notes.trim() || null,
      lines: lines.map(toLineInput),
      version: order?.version,
    };
    setSaving(true);
    try {
      const saved = order ? await orderService.update(order.id, input) : await orderService.create(input);
      toast.success(`Order ${saved.orderNumber} ${order ? "saved" : "created"}`);
      router.push(`/orders/${saved.id}`);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) {
        setErrors(err.fieldErrors);
      } else {
        setFormError(err instanceof ApiError ? err.message : "Could not save the order. Please try again.");
      }
      setSaving(false);
    }
  }

  return (
    <>
      <PageHeader
        title={order ? `Edit order ${order.orderNumber}` : "New order"}
        description="Customer, dates and garments. Totals are recalculated by the server when saved."
        actions={
          <Link href={order ? `/orders/${order.id}` : "/orders"} className="inline-flex h-9 items-center rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50">
            Cancel
          </Link>
        }
      />

      <form onSubmit={submit} className="space-y-6" noValidate>
        {formError && (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {formError}
          </p>
        )}

        <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
          <h2 className="text-sm font-semibold text-slate-900">Customer and dates</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <AsyncSelect
                label="Customer"
                value={customer}
                onChange={chooseCustomer}
                load={loadCustomerOptions}
                placeholder="Type a name, phone or address"
                error={errors.customer}
                required
              />
              {canAddCustomer && (
                <Button variant="ghost" size="sm" onClick={() => setAddingCustomer(true)}>
                  + Add customer
                </Button>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <TextField label="Order date" type="date" value={orderDate} onChange={(e) => setOrderDate(e.target.value)} error={errors.orderDate} required />
              <TextField label="Due date" type="date" value={dueDate} min={orderDate || undefined} onChange={(e) => setDueDate(e.target.value)} error={errors.dueDate} required />
              <TextField label="Trial date" type="date" value={trialDate} onChange={(e) => setTrialDate(e.target.value)} error={errors.trialDate} />
              <TextField label="Delivery date" type="date" value={deliveryDate} onChange={(e) => setDeliveryDate(e.target.value)} error={errors.deliveryDate} />
            </div>
          </div>
        </section>

        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-900">Garments</h2>
          </div>
          {errors.lines && (
            <p className="text-xs text-red-600" role="alert">
              {errors.lines}
            </p>
          )}
          {lines.map((l, i) => (
            <OrderLineEditor
              key={l.key}
              index={i}
              line={l}
              records={records.data}
              customerChosen={customer !== null}
              customerId={customer?.id ?? null}
              errors={lineErrors[l.key] ?? {}}
              onChange={(patch) => updateLine(l.key, patch)}
              onRemove={() => setLines((ls) => ls.filter((x) => x.key !== l.key))}
            />
          ))}
          <Button variant="secondary" onClick={() => setLines((ls) => [...ls, emptyLine()])}>
            Add garment
          </Button>
        </section>

        <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
          <TextAreaField label="Order notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
          <div className="flex items-center justify-between border-t border-slate-200 pt-4">
            <span className="text-sm text-slate-600">Order total</span>
            <span className="text-lg font-semibold tabular-nums text-slate-900">{formatCurrency(total)}</span>
          </div>
        </section>

        <div className="flex justify-end gap-2">
          <Link href={order ? `/orders/${order.id}` : "/orders"} className="inline-flex h-9 items-center rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50">
            Cancel
          </Link>
          <Button type="submit" loading={saving}>
            {order ? "Save changes" : "Save order"}
          </Button>
        </div>
      </form>

      {addingCustomer && (
        <CustomerFormDialog
          record={null}
          onClose={() => setAddingCustomer(false)}
          onSaved={(c) => {
            setAddingCustomer(false);
            chooseCustomer({ id: c.id, label: c.name, detail: c.phone ?? undefined });
            toast.success(`Customer "${c.name}" created`);
          }}
        />
      )}
    </>
  );
}

"use client";

import { useState, type FormEvent } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { SelectField, TextAreaField, TextField } from "@/components/ui/FormField";
import { Spinner } from "@/components/ui/Spinner";
import { useApiQuery } from "@/hooks/useApiQuery";
import { ApiError } from "@/services/apiClient";
import { inventoryService } from "@/services/inventoryService";
import type { AdjustmentReason, StockAdjustment } from "@/types/inventory";
import { ADJUSTMENT_REASON_LABELS, formatSignedQuantity } from "@/utils/labels";
import { loadStockedProductOptions } from "./lookups";

type Mode = "count" | "change";

/** Physical count → difference from system stock; "change" is entered directly (e.g. −2 damaged). */
export function adjustmentFromInput(mode: Mode, value: number, current: number): number {
  return mode === "count" ? value - current : value;
}

interface AdjustmentFormProps {
  initialProduct?: Option | null;
  /** Hides the product picker (used from an inventory row). */
  lockProduct?: boolean;
  onDone: (adjustment: StockAdjustment) => void;
  onCancel?: () => void;
}

export function AdjustmentForm({ initialProduct = null, lockProduct = false, onDone, onCancel }: AdjustmentFormProps) {
  const [product, setProduct] = useState<Option | null>(initialProduct);
  const [mode, setMode] = useState<Mode>("count");
  const [value, setValue] = useState("");
  const [reason, setReason] = useState<AdjustmentReason>("COUNT_CORRECTION");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  const stock = useApiQuery(
    (signal) => (product ? inventoryService.get(product.id, signal) : Promise.resolve(null)),
    [product?.id ?? null],
  );
  const current = stock.data?.quantity;

  const parsed = value.trim() === "" ? null : Number(value);
  const valid = parsed !== null && Number.isInteger(parsed);
  const change = valid && current !== undefined ? adjustmentFromInput(mode, parsed, current) : null;
  const resulting = change !== null && current !== undefined ? current + change : null;

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!product) local.product = "Choose a product";
    if (!valid) local.value = "Enter a whole number";
    else if (mode === "count" && parsed < 0) local.value = "Counted quantity cannot be negative";
    else if (change === 0) local.value = "This would not change the stock";
    else if (resulting !== null && resulting < 0) local.value = `Stock would become ${resulting}; only ${current} available`;
    if (reason === "OTHER" && !notes.trim()) local.notes = "Explain the reason";
    setErrors(local);
    setFormError(null);
    if (Object.keys(local).length === 0) setConfirming(true);
  }

  async function save() {
    if (!product || change === null || current === undefined) return;
    setSaving(true);
    try {
      const result = await inventoryService.adjust({
        productId: product.id,
        adjustmentQuantity: change,
        expectedCurrentQuantity: current,
        reason,
        notes,
      });
      setValue("");
      setNotes("");
      stock.reload();
      onDone(result);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not save the adjustment");
      // Stock may have moved (409): show the latest figure.
      stock.reload();
    } finally {
      setSaving(false);
      setConfirming(false);
    }
  }

  return (
    <>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {formError && (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {formError}
          </p>
        )}
        {!lockProduct && (
          <AsyncSelect
            label="Product"
            value={product}
            onChange={(o) => {
              setProduct(o);
              setFormError(null);
            }}
            load={loadStockedProductOptions}
            placeholder="Search ISBN, title or author"
            error={errors.product}
            required
          />
        )}

        {product && (
          <div className="rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-700">
            {stock.loading && current === undefined ? (
              <Spinner size="sm" label="Loading stock..." />
            ) : (
              <>
                Current stock: <strong className="tabular-nums">{current ?? "-"}</strong>
                {stock.data && !stock.data.active && <span className="ml-2 text-amber-700">(inactive product)</span>}
              </>
            )}
          </div>
        )}

        <fieldset>
          <legend className="mb-2 text-sm font-medium text-slate-700">How do you want to adjust?</legend>
          <div className="flex flex-col gap-2 sm:flex-row sm:gap-6">
            {(
              [
                ["count", "Enter physical count"],
                ["change", "Enter change (+/−)"],
              ] as const
            ).map(([m, label]) => (
              <label key={m} className="flex items-center gap-2 text-sm">
                <input
                  type="radio"
                  name="mode"
                  className="accent-blue-600"
                  checked={mode === m}
                  onChange={() => {
                    setMode(m);
                    setValue("");
                  }}
                />
                {label}
              </label>
            ))}
          </div>
        </fieldset>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label={mode === "count" ? "Counted quantity" : "Change by"}
            type="number"
            inputMode="numeric"
            step="1"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            error={errors.value}
            placeholder={mode === "count" ? "e.g. 8" : "e.g. -2 or 5"}
            required
          />
          <SelectField label="Reason" value={reason} onChange={(e) => setReason(e.target.value as AdjustmentReason)} required>
            {Object.entries(ADJUSTMENT_REASON_LABELS).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </SelectField>
        </div>

        <TextAreaField
          label="Notes"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          error={errors.notes}
          required={reason === "OTHER"}
          rows={2}
        />

        {change !== null && change !== 0 && current !== undefined && (
          <p className="text-sm text-slate-700" aria-live="polite">
            Stock will change from <strong className="tabular-nums">{current}</strong> to{" "}
            <strong className={`tabular-nums ${resulting !== null && resulting < 0 ? "text-red-600" : ""}`}>{resulting}</strong>{" "}
            (<span className={change < 0 ? "text-red-600" : "text-emerald-700"}>{formatSignedQuantity(change)}</span>)
          </p>
        )}

        <div className="flex justify-end gap-2">
          {onCancel && (
            <Button variant="secondary" onClick={onCancel} disabled={saving}>
              Cancel
            </Button>
          )}
          <Button type="submit" disabled={!product || current === undefined}>
            Review adjustment
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={confirming}
        title="Confirm stock adjustment"
        message={
          <div className="space-y-1">
            <p>
              <strong>{product?.label}</strong>
            </p>
            <p>
              {current} → {resulting} ({change !== null ? formatSignedQuantity(change) : ""}) ·{" "}
              {ADJUSTMENT_REASON_LABELS[reason]}
            </p>
            <p className="text-xs text-slate-500">This is recorded permanently in the stock history.</p>
          </div>
        }
        confirmLabel="Save adjustment"
        danger={change !== null && change < 0}
        loading={saving}
        onConfirm={() => void save()}
        onCancel={() => setConfirming(false)}
      />
    </>
  );
}

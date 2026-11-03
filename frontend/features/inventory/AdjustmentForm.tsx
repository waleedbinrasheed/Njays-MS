"use client";

import { useState, type FormEvent } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { SelectField, TextAreaField, TextField } from "@/components/ui/FormField";
import { Spinner } from "@/components/ui/Spinner";
import { loadFabricOptions } from "@/features/master/lookups";
import { useApiQuery } from "@/hooks/useApiQuery";
import { ApiError } from "@/services/apiClient";
import { fabricService } from "@/services/masterDataService";
import { inventoryService } from "@/services/inventoryService";
import type { AdjustmentReason, StockTransaction } from "@/types/inventory";
import { ADJUSTMENT_REASON_LABELS, formatMetres, formatSignedQuantity } from "@/utils/labels";

type Mode = "count" | "change";

const DECIMAL_3 = /^-?\d+(\.\d{1,3})?$/;

/** Physical count → difference from system stock, rounded to the metre's 3 decimals; "change" is entered directly. */
export function adjustmentFromInput(mode: Mode, value: number, current: number): number {
  const raw = mode === "count" ? value - current : value;
  return Math.round(raw * 1000) / 1000;
}

interface AdjustmentFormProps {
  initialProduct?: Option | null;
  onDone: (adjustment: StockTransaction) => void;
  onCancel?: () => void;
}

export function AdjustmentForm({ initialProduct = null, onDone, onCancel }: AdjustmentFormProps) {
  const [product, setProduct] = useState<Option | null>(initialProduct);
  const [mode, setMode] = useState<Mode>("change");
  const [value, setValue] = useState("");
  const [reason, setReason] = useState<AdjustmentReason>("STOCK_IN");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  const stock = useApiQuery(
    (signal) => (product ? fabricService.get(product.id, signal) : Promise.resolve(null)),
    [product?.id ?? null],
  );
  const current = stock.data?.quantityOnHand;

  const valid = DECIMAL_3.test(value.trim());
  const parsed = valid ? Number(value) : null;
  const change = parsed !== null && current !== undefined ? adjustmentFromInput(mode, parsed, current) : null;
  const resulting = change !== null && current !== undefined ? Math.round((current + change) * 1000) / 1000 : null;

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!product) local.product = "Choose a fabric";
    if (!valid) local.value = "Enter metres, up to 3 decimals";
    else if (mode === "count" && parsed !== null && parsed < 0) local.value = "Counted metres cannot be negative";
    else if (change === 0) local.value = "This would not change the stock";
    else if (reason === "STOCK_IN" && change !== null && change < 0) local.value = "Stock-in must add metres";
    else if (resulting !== null && resulting < 0) local.value = `Stock would become ${formatMetres(resulting)}; only ${formatMetres(current)} on hand`;
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
        change,
        reason,
        expectedCurrentQuantity: current,
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
        <AsyncSelect
          label="Fabric"
          value={product}
          onChange={(o) => {
            setProduct(o);
            setFormError(null);
          }}
          load={loadFabricOptions}
          placeholder="Search code or name"
          error={errors.product}
          required
        />

        {product && (
          <div className="rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-700">
            {stock.loading && current === undefined ? (
              <Spinner size="sm" label="Loading stock..." />
            ) : (
              <>
                On hand: <strong className="tabular-nums">{formatMetres(current)}</strong>
                {stock.data && !stock.data.active && <span className="ml-2 text-amber-700">(inactive fabric)</span>}
              </>
            )}
          </div>
        )}

        <fieldset>
          <legend className="mb-2 text-sm font-medium text-slate-700">How do you want to adjust?</legend>
          <div className="flex flex-col gap-2 sm:flex-row sm:gap-6">
            {(
              [
                ["change", "Enter change (+/−)"],
                ["count", "Enter physical count"],
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
            label={mode === "count" ? "Counted metres" : "Change by (metres)"}
            type="number"
            inputMode="decimal"
            step="0.001"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            error={errors.value}
            placeholder={mode === "count" ? "e.g. 12.500" : "e.g. 20 or -2.5"}
            required
          />
          <SelectField label="Reason" value={reason} onChange={(e) => setReason(e.target.value as AdjustmentReason)} required>
            {(Object.keys(ADJUSTMENT_REASON_LABELS) as AdjustmentReason[]).map((key) => (
              <option key={key} value={key}>
                {ADJUSTMENT_REASON_LABELS[key]}
              </option>
            ))}
          </SelectField>
        </div>
        {reason === "STOCK_IN" && (
          <p className="text-xs text-slate-500">Use Stock-in when new fabric arrives at the shop; enter the metres received as a positive change.</p>
        )}

        <TextAreaField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} error={errors.notes} required={reason === "OTHER"} rows={2} />

        {change !== null && change !== 0 && current !== undefined && (
          <p className="text-sm text-slate-700" aria-live="polite">
            Stock will change from <strong className="tabular-nums">{formatMetres(current)}</strong> to{" "}
            <strong className={`tabular-nums ${resulting !== null && resulting < 0 ? "text-red-600" : ""}`}>{formatMetres(resulting)}</strong>{" "}
            (<span className={change < 0 ? "text-red-600" : "text-emerald-700"}>{formatSignedQuantity(change)} m</span>)
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
              {formatMetres(current)} → {formatMetres(resulting)} ({change !== null ? formatSignedQuantity(change) : ""} m) ·{" "}
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

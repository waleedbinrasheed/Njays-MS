"use client";

import Link from "next/link";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { SelectField, TextAreaField, TextField } from "@/components/ui/FormField";
import { GarmentPictorial } from "@/components/measurements/GarmentPictorial";
import { loadFabricOptions, loadGarmentTypeOptions } from "@/features/master/lookups";
import type { Fabric, MeasurementRecord } from "@/types/master";
import { lineAmounts } from "@/utils/orderTotals";
import { formatCurrency, formatDate } from "@/utils/format";
import { FABRIC_SOURCE_LABELS, formatMetres } from "@/utils/labels";
import { num, type LineDraft } from "./orderLineDraft";

interface OrderLineEditorProps {
  index: number;
  line: LineDraft;
  /** Measurement records of the chosen customer (all garment types). */
  records: MeasurementRecord[] | undefined;
  customerChosen: boolean;
  /** Customer id, used for the "take measurements" link. */
  customerId: number | null;
  errors: Partial<Record<"garmentType" | "fabric" | "fabricMetres" | "stitchingCharge", string>>;
  onChange: (patch: Partial<LineDraft>) => void;
  onRemove: () => void;
}

const SOURCES = ["STOCK", "CUSTOMER"] as const;

export function OrderLineEditor({ index, line, records, customerChosen, customerId, errors, onChange, onRemove }: OrderLineEditorProps) {
  const garmentId = line.garmentType?.id ?? null;
  const matching = (records ?? []).filter((r) => r.garmentType.id === garmentId);
  const chosenRecord = matching.find((r) => r.id === line.measurementRecordId) ?? null;
  const metres = num(line.fabricMetres);
  const overStock = line.fabricSource === "STOCK" && line.fabricOnHand !== null && metres > line.fabricOnHand;
  const amounts = lineAmounts({
    fabricFromStock: line.fabricSource === "STOCK",
    fabricMetres: metres,
    fabricPricePerMetre: line.fabricPricePerMetre,
    stitchingCharge: num(line.stitchingCharge),
  });

  function chooseFabric(option: Option | null) {
    const f = option?.data as Fabric | undefined;
    onChange({
      fabric: option,
      fabricPricePerMetre: f ? f.pricePerMetre : line.fabricPricePerMetre,
      fabricOnHand: f ? f.quantityOnHand : null,
    });
  }

  return (
    <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-4" aria-label={`Line ${index + 1}`}>
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-900">Garment {index + 1}</h3>
        <Button variant="ghost" size="sm" className="text-red-600 hover:text-red-700" onClick={onRemove}>
          Remove
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <AsyncSelect
          label="Garment type"
          value={line.garmentType}
          onChange={(o) => onChange({ garmentType: o, measurementRecordId: null })}
          load={loadGarmentTypeOptions}
          error={errors.garmentType}
          required
        />
        <TextField
          label="Stitching charge (PKR)"
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          value={line.stitchingCharge}
          onChange={(e) => onChange({ stitchingCharge: e.target.value })}
          error={errors.stitchingCharge}
        />
      </div>

      <fieldset>
        <legend className="mb-2 text-sm font-medium text-slate-700">Fabric</legend>
        <div className="flex flex-col gap-2 sm:flex-row sm:gap-6">
          {SOURCES.map((s) => (
            <label key={s} className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name={`fabric-source-${line.key}`}
                className="accent-blue-600"
                checked={line.fabricSource === s}
                onChange={() => onChange({ fabricSource: s })}
              />
              {FABRIC_SOURCE_LABELS[s]}
            </label>
          ))}
        </div>
      </fieldset>

      {line.fabricSource === "STOCK" ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <AsyncSelect
            label="Fabric"
            value={line.fabric}
            onChange={chooseFabric}
            load={loadFabricOptions}
            error={errors.fabric}
            required
          />
          <div>
            <TextField
              label="Metres"
              type="number"
              inputMode="decimal"
              min="0"
              step="0.001"
              value={line.fabricMetres}
              onChange={(e) => onChange({ fabricMetres: e.target.value })}
              error={errors.fabricMetres}
              required
            />
            {overStock && (
              <p className="mt-1 text-xs font-medium text-amber-700" role="status">
                Only {formatMetres(line.fabricOnHand)} on hand
              </p>
            )}
          </div>
          <p className="text-sm text-slate-600 sm:col-span-2">
            Fabric price {formatCurrency(line.fabricPricePerMetre)} per metre · fabric amount{" "}
            <span className="font-medium text-slate-900">{formatCurrency(amounts.fabricAmount)}</span>
          </p>
        </div>
      ) : (
        <TextAreaField
          label="Customer's fabric"
          rows={2}
          placeholder="e.g. Blue cotton, 2.5 m, sent with customer"
          value={line.customerFabricNote}
          onChange={(e) => onChange({ customerFabricNote: e.target.value })}
        />
      )}

      <div className="space-y-2">
        <SelectField
          label="Measurement record"
          value={line.measurementRecordId ?? ""}
          onChange={(e) => onChange({ measurementRecordId: e.target.value ? Number(e.target.value) : null })}
          disabled={!customerChosen || !garmentId}
          hint={
            !customerChosen
              ? "Choose the customer first."
              : !garmentId
                ? "Choose the garment type first."
                : undefined
          }
        >
          <option value="">No measurements on this order</option>
          {matching.map((r) => (
            <option key={r.id} value={r.id}>
              Taken {formatDate(r.takenOn)}
              {r.notes ? ` · ${r.notes}` : ""}
            </option>
          ))}
        </SelectField>
        {customerChosen && garmentId && records && matching.length === 0 && (
          <p className="text-xs text-slate-500">
            No {line.garmentType?.label.toLowerCase()} measurements for this customer yet.{" "}
            {customerId && (
              <Link href={`/master/customers/${customerId}`} className="text-blue-700 hover:underline">
                Take measurements on the customer page
              </Link>
            )}
          </p>
        )}
        {chosenRecord && (
          <div className="rounded-md bg-slate-50 p-3">
            <GarmentPictorial
              shape={chosenRecord.garmentType.shape}
              measurements={chosenRecord.values.map((v) => ({ label: v.label, value: v.value, sortOrder: v.sortOrder }))}
            />
          </div>
        )}
      </div>

      <TextAreaField label="Line notes" rows={2} value={line.notes} onChange={(e) => onChange({ notes: e.target.value })} />

      <p className="text-right text-sm text-slate-700">
        Line total <span className="font-semibold tabular-nums text-slate-900">{formatCurrency(amounts.lineTotal)}</span>
      </p>
    </section>
  );
}

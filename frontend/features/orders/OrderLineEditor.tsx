"use client";

import Link from "next/link";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { SelectField, TextField } from "@/components/ui/FormField";
import { DressMeasurements, DressPicture } from "@/components/measurements/DressMeasurements";
import { loadFabricOptions, loadGarmentTypeOptions } from "@/features/master/lookups";
import type { Fabric, GarmentType, MeasurementRecord } from "@/types/master";
import { lineAmounts } from "@/utils/orderTotals";
import { formatCurrency, formatDate } from "@/utils/format";
import { FABRIC_SOURCE_LABELS, formatMetres } from "@/utils/labels";
import { num, type LineDraft } from "./orderLineDraft";

interface OrderLineEditorProps {
  index: number;
  line: LineDraft;
  /** Measurement records of the chosen customer (all dress types). */
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
  const dressId = line.garmentType?.id ?? null;
  // undefined: no dress chosen. null: a dress the user added, which has no picture.
  const dressCode = line.garmentType ? ((line.garmentType.data as { code?: string | null } | undefined)?.code ?? null) : undefined;
  const matching = (records ?? []).filter((r) => r.garmentType.id === dressId);
  const chosenRecord = matching.find((r) => r.id === line.measurementRecordId) ?? null;
  const metres = num(line.fabricMetres);
  const overStock = line.fabricSource === "STOCK" && line.fabricOnHand !== null && metres > line.fabricOnHand;
  const amounts = lineAmounts({
    fabricFromStock: line.fabricSource === "STOCK",
    fabricMetres: metres,
    fabricPricePerMetre: line.fabricPricePerMetre,
    stitchingCharge: num(line.stitchingCharge),
  });

  /** The dress's default price fills the stitching charge; the price stays editable on the order. */
  function chooseDress(option: Option | null) {
    const dress = option?.data as GarmentType | undefined;
    onChange({
      garmentType: option,
      measurementRecordId: null,
      stitchingCharge: dress ? String(dress.defaultPrice) : line.stitchingCharge,
    });
  }

  function chooseFabric(option: Option | null) {
    const f = option?.data as Fabric | undefined;
    onChange({
      fabric: option,
      fabricPricePerMetre: f ? f.pricePerMetre : line.fabricPricePerMetre,
      fabricOnHand: f ? f.quantityOnHand : null,
    });
  }

  return (
    <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4" aria-label={`Dress ${index + 1}`}>
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-900">Dress {index + 1}</h3>
        <Button variant="ghost" size="sm" className="text-red-600 hover:text-red-700" onClick={onRemove}>
          Remove
        </Button>
      </div>

      {/* Dress, price and measurements on one row on wide screens. */}
      <div className="grid gap-3 md:grid-cols-[minmax(0,1.4fr)_minmax(0,0.7fr)_minmax(0,1.4fr)]">
        <AsyncSelect
          label="Dress type"
          value={line.garmentType}
          onChange={chooseDress}
          load={loadGarmentTypeOptions}
          error={errors.garmentType}
          required
        />
        <TextField
          label="Price (PKR)"
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          value={line.stitchingCharge}
          onChange={(e) => onChange({ stitchingCharge: e.target.value })}
          error={errors.stitchingCharge}
        />
        <SelectField
          label="Measurements"
          value={line.measurementRecordId ?? ""}
          onChange={(e) => onChange({ measurementRecordId: e.target.value ? Number(e.target.value) : null })}
          disabled={!customerChosen || !dressId}
          hint={!customerChosen ? "Choose the customer first." : !dressId ? "Choose the dress type first." : undefined}
        >
          <option value="">No measurements on this order</option>
          {matching.map((r) => (
            <option key={r.id} value={r.id}>
              Taken {formatDate(r.takenOn)}
              {r.notes ? ` · ${r.notes}` : ""}
            </option>
          ))}
        </SelectField>
      </div>
      {customerChosen && dressId && records && matching.length === 0 && (
        <p className="text-xs text-slate-500">
          No {line.garmentType?.label.toLowerCase()} measurements for this customer yet.{" "}
          {customerId && (
            <Link href={`/master/customers/${customerId}`} className="text-blue-700 hover:underline">
              Take measurements on the customer page
            </Link>
          )}
        </p>
      )}

      {/* The reference picture shows as soon as the dress is chosen; with a measurement record it sits in the block below. */}
      {dressCode && !chosenRecord && (
        <div className="rounded-md bg-slate-50 p-3">
          <DressPicture code={dressCode} dressName={line.garmentType?.label ?? ""} />
        </div>
      )}

      {/* Fabric: source, then the fabric and metres on the same line. */}
      <div className="flex flex-wrap items-end gap-3">
        <fieldset className="flex items-center gap-4 pb-2 text-sm">
          <legend className="sr-only">Fabric source</legend>
          {SOURCES.map((s) => (
            <label key={s} className="flex items-center gap-2">
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
        </fieldset>
        {line.fabricSource === "STOCK" ? (
          <>
            <div className="min-w-[12rem] flex-1">
              <AsyncSelect label="Fabric" value={line.fabric} onChange={chooseFabric} load={loadFabricOptions} error={errors.fabric} required />
            </div>
            <div className="w-36">
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
            </div>
          </>
        ) : (
          <div className="min-w-[12rem] flex-1">
            <TextField
              label="Customer's fabric"
              placeholder="e.g. Blue cotton, 2.5 m"
              value={line.customerFabricNote}
              onChange={(e) => onChange({ customerFabricNote: e.target.value })}
            />
          </div>
        )}
      </div>
      {line.fabricSource === "STOCK" && (
        <p className="text-sm text-slate-600">
          Fabric {formatCurrency(line.fabricPricePerMetre)} per metre · fabric amount{" "}
          <span className="font-medium text-slate-900">{formatCurrency(amounts.fabricAmount)}</span>
          {overStock && (
            <span className="ml-3 font-medium text-amber-700" role="status">
              Only {formatMetres(line.fabricOnHand)} on hand
            </span>
          )}
        </p>
      )}

      <TextField label="Notes" value={line.notes} onChange={(e) => onChange({ notes: e.target.value })} />

      {chosenRecord && (
        <div className="rounded-md bg-slate-50 p-3">
          <DressMeasurements
            code={chosenRecord.garmentType.code}
            dressName={chosenRecord.garmentType.name}
            measurements={chosenRecord.values.map((v) => ({ label: v.label, value: v.value, sortOrder: v.sortOrder }))}
          />
        </div>
      )}

      <p className="text-right text-sm text-slate-700">
        Line total <span className="font-semibold tabular-nums text-slate-900">{formatCurrency(amounts.lineTotal)}</span>
      </p>
    </section>
  );
}

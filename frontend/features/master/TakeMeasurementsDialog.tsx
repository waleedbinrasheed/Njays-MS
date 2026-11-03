"use client";

import { useState, type FormEvent } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { TextAreaField, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { ApiError } from "@/services/apiClient";
import { measurementService } from "@/services/masterDataService";
import type { GarmentType } from "@/types/master";
import { todayIso } from "@/utils/format";
import { loadGarmentTypeOptions } from "./lookups";

const NUMBER = /^\d+(\.\d{1,2})?$/;

interface TakeMeasurementsDialogProps {
  customerId: number;
  customerName: string;
  onClose: () => void;
  onSaved: (garmentName: string) => void;
}

/**
 * Choose a garment type, then enter one value per active measurement point (inches). Saved records
 * are read-only: there is no edit or delete, so a correction is a new record.
 */
export function TakeMeasurementsDialog({ customerId, customerName, onClose, onSaved }: TakeMeasurementsDialogProps) {
  const [garment, setGarment] = useState<Option | null>(null);
  const [values, setValues] = useState<Record<number, string>>({});
  const [takenOn, setTakenOn] = useState(todayIso());
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const type = garment?.data as GarmentType | undefined;
  const points = (type?.points ?? []).filter((p) => p.active).sort((a, b) => a.sortOrder - b.sortOrder);

  function chooseGarment(o: Option | null) {
    setGarment(o);
    setValues({});
    setErrors({});
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!type) {
      setErrors({ garment: "Choose the garment type" });
      return;
    }
    const local: Record<string, string> = {};
    if (!takenOn) local.takenOn = "Date is required";
    else if (takenOn > todayIso()) local.takenOn = "Date cannot be in the future";
    const entries: { pointId: number; value: number }[] = [];
    for (const p of points) {
      const raw = (values[p.id] ?? "").trim();
      if (!NUMBER.test(raw) || Number(raw) <= 0) {
        local[`point-${p.id}`] = "Enter inches above zero (up to 2 decimals)";
      } else {
        entries.push({ pointId: p.id, value: Number(raw) });
      }
    }
    setErrors(local);
    setFormError(null);
    if (Object.keys(local).length) return;

    setSaving(true);
    try {
      await measurementService.takeForCustomer(customerId, { garmentTypeId: type.id, takenOn, notes: notes.trim(), values: entries });
      onSaved(type.name);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not save the measurements");
      setSaving(false);
    }
  }

  return (
    <FormDialog
      title={`Take measurements · ${customerName}`}
      submitLabel="Save measurements"
      saving={saving}
      formError={formError}
      onSubmit={submit}
      onClose={onClose}
      size="lg"
    >
      <AsyncSelect
        label="Garment type"
        value={garment}
        onChange={chooseGarment}
        load={loadGarmentTypeOptions}
        error={errors.garment}
        required
      />

      {type && points.length === 0 && (
        <p className="text-sm text-amber-700">This garment type has no active measurement points. Add them under Garment Types.</p>
      )}

      {type && points.length > 0 && (
        <div className="grid gap-3 sm:grid-cols-2">
          {points.map((p) => (
            <TextField
              key={p.id}
              label={`${p.label} (in)`}
              type="number"
              inputMode="decimal"
              min="0"
              step="0.01"
              value={values[p.id] ?? ""}
              onChange={(e) => setValues((v) => ({ ...v, [p.id]: e.target.value }))}
              error={errors[`point-${p.id}`]}
              required
            />
          ))}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Date taken" type="date" max={todayIso()} value={takenOn} onChange={(e) => setTakenOn(e.target.value)} error={errors.takenOn} required />
      </div>
      <TextAreaField label="Notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
    </FormDialog>
  );
}

"use client";

import { Badge } from "@/components/ui/Feedback";
import { Button } from "@/components/ui/Button";
import { Checkbox, TextAreaField, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { useEntityForm } from "@/hooks/useEntityForm";
import { GarmentSilhouette } from "@/components/measurements/GarmentPictorial";
import { garmentTypeService } from "@/services/masterDataService";
import type { GarmentShape, GarmentType, GarmentTypeInput } from "@/types/master";
import { GARMENT_SHAPE_LABELS } from "@/utils/labels";
import { EntityListPage, type Column, type FormRenderProps } from "./EntityListPage";

const SHAPES: GarmentShape[] = ["TOP", "BOTTOM", "FULL"];

interface PointRow {
  key: number;
  id: number | null;
  label: string;
  sortOrder: string;
  active: boolean;
}

interface GarmentFormValues {
  name: string;
  description: string;
  shape: GarmentShape;
  active: boolean;
  points: PointRow[];
  version?: number;
}

let nextRowKey = 1;

function shapeSummary(shape: GarmentShape) {
  return GARMENT_SHAPE_LABELS[shape].split(" (")[0];
}

/** Pick Top / Bottom / Full by its silhouette, so the choice is visible without reading the words. */
function ShapePicker({ value, onChange, error }: { value: GarmentShape; onChange: (s: GarmentShape) => void; error?: string }) {
  return (
    <fieldset>
      <legend className="mb-2 text-sm font-medium text-slate-700">Shape</legend>
      <div className="grid grid-cols-3 gap-2">
        {SHAPES.map((s) => (
          <label
            key={s}
            className={`flex cursor-pointer flex-col items-center gap-2 rounded-md border p-3 text-center text-xs ${
              value === s ? "border-blue-500 bg-blue-50 text-blue-800" : "border-slate-300 bg-white text-slate-600 hover:bg-slate-50"
            }`}
          >
            <input type="radio" name="garment-shape" className="sr-only" checked={value === s} onChange={() => onChange(s)} />
            <GarmentSilhouette shape={s} className="h-16 w-auto" />
            <span className="font-medium">{shapeSummary(s)}</span>
          </label>
        ))}
      </div>
      <p className="mt-2 text-xs text-slate-500">{GARMENT_SHAPE_LABELS[value]}</p>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </fieldset>
  );
}

function PointsEditor({
  rows,
  onChange,
  errors,
}: {
  rows: PointRow[];
  onChange: (rows: PointRow[]) => void;
  errors: Record<string, string>;
}) {
  const update = (key: number, patch: Partial<PointRow>) => onChange(rows.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const inputClass = "block w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm";

  return (
    <fieldset className="space-y-2">
      <legend className="mb-1 text-sm font-medium text-slate-700">Measurement points</legend>
      <p className="text-xs text-slate-500">
        Points used by existing measurement records are kept and deactivated rather than deleted, so old records stay readable.
      </p>
      {rows.length === 0 && <p className="text-sm text-slate-500">No points yet.</p>}
      {rows.map((r, i) => (
        <div key={r.key} className="grid grid-cols-[1fr_5rem_auto_auto] items-start gap-2">
          <div>
            <label className="sr-only" htmlFor={`point-label-${r.key}`}>
              Point {i + 1} label
            </label>
            <input
              id={`point-label-${r.key}`}
              className={inputClass}
              placeholder="e.g. Chest"
              value={r.label}
              onChange={(e) => update(r.key, { label: e.target.value })}
              aria-invalid={errors[`label-${r.key}`] ? true : undefined}
            />
            {errors[`label-${r.key}`] && <p className="mt-1 text-xs text-red-600">{errors[`label-${r.key}`]}</p>}
          </div>
          <div>
            <label className="sr-only" htmlFor={`point-order-${r.key}`}>
              Point {i + 1} sort order
            </label>
            <input
              id={`point-order-${r.key}`}
              type="number"
              min="1"
              step="1"
              className={inputClass}
              placeholder="Order"
              value={r.sortOrder}
              onChange={(e) => update(r.key, { sortOrder: e.target.value })}
            />
          </div>
          <label className="flex items-center gap-1.5 pt-2 text-xs text-slate-700">
            <input type="checkbox" className="accent-blue-600" checked={r.active} onChange={(e) => update(r.key, { active: e.target.checked })} />
            Active
          </label>
          <button
            type="button"
            className="rounded p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600"
            aria-label={`Remove point ${r.label || i + 1}`}
            onClick={() => onChange(rows.filter((x) => x.key !== r.key))}
          >
            ×
          </button>
        </div>
      ))}
      <Button
        variant="secondary"
        size="sm"
        onClick={() =>
          onChange([...rows, { key: nextRowKey++, id: null, label: "", sortOrder: String(rows.length + 1), active: true }])
        }
      >
        Add point
      </Button>
    </fieldset>
  );
}

function GarmentTypeForm({ record, onClose, onSaved }: FormRenderProps<GarmentType>) {
  const form = useEntityForm<GarmentFormValues, GarmentType>({
    initial: {
      name: record?.name ?? "",
      description: record?.description ?? "",
      shape: record?.shape ?? "TOP",
      active: record?.active ?? true,
      points: (record?.points ?? []).map((p) => ({
        key: nextRowKey++,
        id: p.id,
        label: p.label,
        sortOrder: String(p.sortOrder),
        active: p.active,
      })),
      version: record?.version,
    },
    validate: (v) => {
      const out: Record<string, string> = {
        name: v.name.trim() ? "" : "Name is required",
      };
      for (const p of v.points) {
        if (!p.label.trim()) out[`label-${p.key}`] = "Enter a label for this point";
      }
      return out;
    },
    save: (v) => {
      const input: GarmentTypeInput = {
        name: v.name.trim(),
        description: v.description.trim(),
        shape: v.shape,
        active: v.active,
        points: v.points.map((p) => ({
          id: p.id,
          label: p.label.trim(),
          sortOrder: Number(p.sortOrder) || 0,
          active: p.active,
        })),
        version: v.version,
      };
      return record ? garmentTypeService.update(record.id, input) : garmentTypeService.create(input);
    },
    onSaved: (saved) => onSaved(saved, !record),
  });
  const { values: v, set, errors } = form;

  return (
    <FormDialog
      title={record ? "Edit garment type" : "New garment type"}
      submitLabel={record ? "Save changes" : "Create garment type"}
      saving={form.saving}
      formError={form.formError}
      onSubmit={form.submit}
      onClose={onClose}
      size="lg"
    >
      {record?.inUse && (
        <p className="rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">
          This garment type is used on orders or measurement records. Removing a point deactivates it instead of deleting it.
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Name" value={v.name} onChange={(e) => set("name", e.target.value)} error={errors.name} required autoFocus />
        <Checkbox label="Active" description="Inactive types cannot be chosen for new orders." checked={v.active} onChange={(e) => set("active", e.target.checked)} />
      </div>
      <TextAreaField label="Description" rows={2} value={v.description} onChange={(e) => set("description", e.target.value)} error={errors.description} />
      <ShapePicker value={v.shape} onChange={(s) => set("shape", s)} error={errors.shape} />
      <PointsEditor rows={v.points} onChange={(rows) => set("points", rows)} errors={errors} />
    </FormDialog>
  );
}

const COLUMNS: Column<GarmentType>[] = [
  { header: "Name", sortField: "name", render: (g) => <span className="font-medium text-slate-900">{g.name}</span> },
  { header: "Shape", render: (g) => shapeSummary(g.shape) },
  { header: "Points", className: "text-right tabular-nums", render: (g) => g.points.filter((p) => p.active).length },
  { header: "Description", render: (g) => g.description ?? "-" },
  { header: "In use", render: (g) => (g.inUse ? <Badge tone="blue">In use</Badge> : "-") },
];

export function GarmentTypesPage() {
  return (
    <EntityListPage<GarmentType>
      title="Garment types"
      description="Kinds of garment the shop makes, with the measurement points taken for each."
      noun="garment type"
      list={garmentTypeService.list}
      remove={garmentTypeService.remove}
      columns={COLUMNS}
      defaultSort={{ field: "name", direction: "asc" }}
      searchPlaceholder="Search garment types"
      displayName={(g) => g.name}
      canDelete={(g) => !g.inUse}
      renderForm={(props) => <GarmentTypeForm {...props} />}
    />
  );
}


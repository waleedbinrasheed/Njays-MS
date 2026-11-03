"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/Feedback";
import { Checkbox, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { useEntityForm } from "@/hooks/useEntityForm";
import { fabricService } from "@/services/masterDataService";
import type { Fabric, FabricInput } from "@/types/master";
import { formatCurrency } from "@/utils/format";
import { EntityListPage, type Column, type FormRenderProps } from "./EntityListPage";

interface FabricFormValues {
  code: string;
  name: string;
  material: string;
  colour: string;
  pattern: string;
  width: string;
  costPerMetre: string;
  pricePerMetre: string;
  reorderLevel: string;
  active: boolean;
  version?: number;
}

const NUMBER = /^\d+(\.\d{1,3})?$/;

/** Empty optional numbers go out as null; the backend keeps the field unset. */
const optional = (s: string) => (s.trim() === "" ? null : Number(s));

function FabricForm({ record, onClose, onSaved }: FormRenderProps<Fabric>) {
  const form = useEntityForm<FabricFormValues, Fabric>({
    initial: {
      code: record?.code ?? "",
      name: record?.name ?? "",
      material: record?.material ?? "",
      colour: record?.colour ?? "",
      pattern: record?.pattern ?? "",
      width: record?.width?.toString() ?? "",
      costPerMetre: record ? String(record.costPerMetre) : "",
      pricePerMetre: record ? String(record.pricePerMetre) : "",
      reorderLevel: record ? String(record.reorderLevel) : "",
      active: record?.active ?? true,
      version: record?.version,
    },
    validate: (v) => {
      const price = Number(v.pricePerMetre);
      return {
        code: v.code.trim() ? "" : "Code is required",
        name: v.name.trim() ? "" : "Name is required",
        pricePerMetre: NUMBER.test(v.pricePerMetre.trim()) && price > 0 ? "" : "Price per metre must be above zero",
        costPerMetre: v.costPerMetre.trim() === "" || NUMBER.test(v.costPerMetre.trim()) ? "" : "Enter a valid amount",
        width: v.width.trim() === "" || NUMBER.test(v.width.trim()) ? "" : "Enter a valid width",
        reorderLevel: v.reorderLevel.trim() === "" || NUMBER.test(v.reorderLevel.trim()) ? "" : "Enter metres, up to 3 decimals",
      };
    },
    save: (v) => {
      const input: FabricInput = {
        code: v.code.trim(),
        name: v.name.trim(),
        material: v.material.trim(),
        colour: v.colour.trim(),
        pattern: v.pattern.trim(),
        width: optional(v.width),
        costPerMetre: optional(v.costPerMetre),
        pricePerMetre: optional(v.pricePerMetre),
        reorderLevel: optional(v.reorderLevel),
        active: v.active,
        version: v.version,
      };
      return record ? fabricService.update(record.id, input) : fabricService.create(input);
    },
    onSaved: (saved) => onSaved(saved, !record),
  });
  const { values: v, set, errors } = form;

  return (
    <FormDialog
      title={record ? "Edit fabric" : "New fabric"}
      submitLabel={record ? "Save changes" : "Create fabric"}
      saving={form.saving}
      formError={form.formError}
      onSubmit={form.submit}
      onClose={onClose}
      size="lg"
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Code" value={v.code} onChange={(e) => set("code", e.target.value)} error={errors.code} required autoFocus />
        <TextField label="Name" value={v.name} onChange={(e) => set("name", e.target.value)} error={errors.name} required />
        <TextField label="Material" value={v.material} onChange={(e) => set("material", e.target.value)} error={errors.material} placeholder="e.g. Cotton" />
        <TextField label="Colour" value={v.colour} onChange={(e) => set("colour", e.target.value)} error={errors.colour} />
        <TextField label="Pattern" value={v.pattern} onChange={(e) => set("pattern", e.target.value)} error={errors.pattern} placeholder="e.g. Plain, checked" />
        <TextField
          label="Width (inches)"
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          value={v.width}
          onChange={(e) => set("width", e.target.value)}
          error={errors.width}
        />
        <TextField
          label="Cost per metre (PKR)"
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          value={v.costPerMetre}
          onChange={(e) => set("costPerMetre", e.target.value)}
          error={errors.costPerMetre}
        />
        <TextField
          label="Price per metre (PKR)"
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          value={v.pricePerMetre}
          onChange={(e) => set("pricePerMetre", e.target.value)}
          error={errors.pricePerMetre}
          required
        />
        <TextField
          label="Reorder level (metres)"
          type="number"
          inputMode="decimal"
          min="0"
          step="0.001"
          value={v.reorderLevel}
          onChange={(e) => set("reorderLevel", e.target.value)}
          error={errors.reorderLevel}
          hint="Stock at or below this many metres is flagged as low."
        />
      </div>
      <Checkbox
        label="Active"
        description="Inactive fabrics cannot be chosen for new orders."
        checked={v.active}
        onChange={(e) => set("active", e.target.checked)}
      />
    </FormDialog>
  );
}

const COLUMNS: Column<Fabric>[] = [
  { header: "Code", sortField: "code", render: (f) => <span className="font-mono text-xs font-medium text-slate-900">{f.code}</span> },
  { header: "Name", sortField: "name", render: (f) => f.name },
  {
    header: "Material / colour",
    render: (f) => [f.material, f.colour, f.pattern].filter(Boolean).join(" · ") || "-",
  },
  { header: "Price / m", sortField: "pricePerMetre", className: "text-right tabular-nums", render: (f) => formatCurrency(f.pricePerMetre) },
  {
    header: "On hand (m)",
    className: "text-right",
    render: (f) => (
      <span className="inline-flex items-center gap-2 tabular-nums">
        {f.quantityOnHand.toFixed(3)}
        {f.lowStock && <Badge tone="amber">Low</Badge>}
      </span>
    ),
  },
];

export function FabricsPage() {
  const [lowOnly, setLowOnly] = useState(false);
  return (
    <EntityListPage<Fabric>
      title="Fabrics"
      description="Fabrics kept in stock and sold to customers by the metre."
      noun="fabric"
      list={fabricService.list}
      remove={fabricService.remove}
      columns={COLUMNS}
      defaultSort={{ field: "code", direction: "asc" }}
      searchPlaceholder="Search code, name, colour or material"
      displayName={(f) => f.name}
      renderForm={(props) => <FabricForm {...props} />}
      filterQuery={{ lowStockOnly: lowOnly || undefined }}
      filters={
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" className="accent-blue-600" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} />
          Low stock only
        </label>
      }
    />
  );
}

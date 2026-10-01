"use client";

import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/Feedback";
import { Checkbox, TextAreaField, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { useEntityForm } from "@/hooks/useEntityForm";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { representativeService } from "@/services/masterDataService";
import type { Representative, RepresentativeInput } from "@/types/master";
import { EntityListPage, type FormRenderProps } from "./EntityListPage";

export function RepresentativeForm({ record, onClose, onSaved }: FormRenderProps<Representative>) {
  const form = useEntityForm<RepresentativeInput, Representative>({
    initial: {
      name: record?.name ?? "",
      phone: record?.phone ?? "",
      email: record?.email ?? "",
      address: record?.address ?? "",
      area: record?.area ?? "",
      active: record?.active ?? true,
      version: record?.version,
    },
    validate: (v) => (v.name.trim() ? {} : { name: "Name is required" }),
    save: (v) => (record ? representativeService.update(record.id, v) : representativeService.create(v)),
    onSaved: (saved) => onSaved(saved, !record),
  });
  const { values: v, set, errors } = form;

  return (
    <FormDialog
      title={record ? "Edit representative" : "New representative"}
      submitLabel={record ? "Save changes" : "Create representative"}
      saving={form.saving}
      formError={form.formError}
      onSubmit={form.submit}
      onClose={onClose}
    >
      <TextField label="Name" value={v.name} onChange={(e) => set("name", e.target.value)} error={errors.name} required autoFocus />
      <TextField
        label="Area"
        value={v.area}
        onChange={(e) => set("area", e.target.value)}
        error={errors.area}
        placeholder="e.g. Lahore - Gulberg"
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Phone" type="tel" value={v.phone} onChange={(e) => set("phone", e.target.value)} error={errors.phone} />
        <TextField label="Email" type="email" value={v.email} onChange={(e) => set("email", e.target.value)} error={errors.email} />
      </div>
      <TextAreaField label="Address" value={v.address} onChange={(e) => set("address", e.target.value)} error={errors.address} />
      {record?.builtIn ? (
        <p className="text-xs text-slate-500">Counter Sales is built in for sales over the shop counter and is always active.</p>
      ) : (
        <Checkbox
          label="Active"
          description="Inactive representatives keep their history but cannot be given customers or sold to."
          checked={v.active}
          onChange={(e) => set("active", e.target.checked)}
        />
      )}
    </FormDialog>
  );
}

export function RepresentativesPage() {
  const [area, setArea] = useState("");
  const debouncedArea = useDebouncedValue(area);

  return (
    <EntityListPage<Representative>
      title="Representatives"
      description="Representatives buy from the shop for their own customers. Sales are billed to them."
      noun="representative"
      list={representativeService.list}
      remove={representativeService.remove}
      columns={[
        {
          header: "Name",
          sortField: "name",
          render: (r) => (
            <Link href={`/master/representatives/${r.id}`} className="font-medium text-blue-700 hover:underline">
              {r.name}
              {r.builtIn && (
                <span className="ml-2">
                  <Badge tone="blue">Built-in</Badge>
                </span>
              )}
            </Link>
          ),
        },
        { header: "Area", sortField: "area", render: (r) => r.area ?? "-" },
        {
          header: "Phone / email",
          render: (r) => (
            <>
              <div>{r.phone ?? "-"}</div>
              {r.email && <div className="text-xs text-slate-500">{r.email}</div>}
            </>
          ),
        },
        { header: "Customers", className: "text-right tabular-nums", render: (r) => r.customerCount },
      ]}
      defaultSort={{ field: "name", direction: "asc" }}
      searchPlaceholder="Search name, area, phone or email"
      displayName={(r) => r.name}
      canDelete={(r) => !r.builtIn && r.customerCount === 0}
      renderForm={(props) => <RepresentativeForm {...props} />}
      filterQuery={{ area: debouncedArea || undefined }}
      filters={
        <input
          type="search"
          value={area}
          onChange={(e) => setArea(e.target.value)}
          placeholder="Filter by area"
          aria-label="Filter by area"
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm sm:w-48"
        />
      }
    />
  );
}

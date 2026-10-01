"use client";

import Link from "next/link";
import { useState } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Badge } from "@/components/ui/Feedback";
import { Checkbox, TextAreaField, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { useEntityForm } from "@/hooks/useEntityForm";
import { customerService } from "@/services/masterDataService";
import type { Customer, CustomerInput } from "@/types/master";
import { EntityListPage, type FormRenderProps } from "./EntityListPage";
import { FilterSelect } from "./FilterSelect";
import { loadAnyRepresentativeOptions, loadRepresentativeOptions } from "./lookups";

interface CustomerFormValues extends Omit<CustomerInput, "representativeId"> {
  representative: Option | null;
}

function CustomerForm({ record, onClose, onSaved }: FormRenderProps<Customer>) {
  const walkIn = record?.walkIn ?? false;
  const form = useEntityForm<CustomerFormValues, Customer>({
    initial: {
      name: record?.name ?? "",
      phone: record?.phone ?? "",
      email: record?.email ?? "",
      address: record?.address ?? "",
      representative: record?.representative ? { id: record.representative.id, label: record.representative.name } : null,
      active: record?.active ?? true,
      version: record?.version,
    },
    validate: (v) => ({
      name: v.name.trim() ? undefined : "Name is required",
      representative: walkIn || v.representative ? undefined : "Choose the customer's representative",
    }),
    save: ({ representative, ...v }) => {
      const input: CustomerInput = { ...v, representativeId: representative?.id ?? null };
      return record ? customerService.update(record.id, input) : customerService.create(input);
    },
    onSaved: (saved) => onSaved(saved, !record),
  });
  const { values: v, set, errors } = form;

  return (
    <FormDialog
      title={record ? "Edit customer" : "New customer"}
      submitLabel={record ? "Save changes" : "Create customer"}
      saving={form.saving}
      formError={form.formError}
      onSubmit={form.submit}
      onClose={onClose}
    >
      <TextField label="Name" value={v.name} onChange={(e) => set("name", e.target.value)} error={errors.name} required autoFocus />
      {!walkIn && (
        <AsyncSelect
          label="Representative"
          value={v.representative}
          onChange={(o) => set("representative", o)}
          load={loadRepresentativeOptions}
          error={errors.representative}
          required
        />
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Phone" type="tel" value={v.phone} onChange={(e) => set("phone", e.target.value)} error={errors.phone} />
        <TextField label="Email" type="email" value={v.email} onChange={(e) => set("email", e.target.value)} error={errors.email} />
      </div>
      <TextAreaField label="Address" value={v.address} onChange={(e) => set("address", e.target.value)} error={errors.address} />
      {walkIn ? (
        <p className="text-xs text-slate-500">
          The walk-in customer is kept for invoices made before representatives were introduced and is always active.
        </p>
      ) : (
        <Checkbox
          label="Active"
          description="Inactive customers cannot be chosen for new sales."
          checked={v.active}
          onChange={(e) => set("active", e.target.checked)}
        />
      )}
    </FormDialog>
  );
}

export function CustomersPage() {
  const [representative, setRepresentative] = useState<Option | null>(null);
  const [unassigned, setUnassigned] = useState(false);

  return (
    <EntityListPage<Customer>
      title="Customers"
      description="Each customer belongs to a representative; sales are billed to the representative."
      noun="customer"
      list={customerService.list}
      remove={customerService.remove}
      columns={[
        {
          header: "Name",
          sortField: "name",
          render: (c) => (
            <span className="font-medium text-slate-900">
              {c.name} {c.walkIn && <Badge>Legacy walk-in</Badge>}
            </span>
          ),
        },
        {
          header: "Representative",
          render: (c) =>
            c.representative ? (
              <Link href={`/master/representatives/${c.representative.id}`} className="text-blue-700 hover:underline">
                {c.representative.name}
              </Link>
            ) : c.walkIn ? (
              "-"
            ) : (
              <Badge tone="amber">Unassigned</Badge>
            ),
        },
        { header: "Phone", sortField: "phone", render: (c) => c.phone ?? "-" },
        { header: "Email", render: (c) => c.email ?? "-" },
      ]}
      defaultSort={{ field: "name", direction: "asc" }}
      searchPlaceholder="Search name, phone or email"
      displayName={(c) => c.name}
      canDelete={(c) => !c.walkIn}
      renderForm={(props) => <CustomerForm {...props} />}
      filterQuery={{ representativeId: unassigned ? undefined : representative?.id, unassigned: unassigned || undefined }}
      filters={
        <>
          {!unassigned && (
            <div className="sm:w-56">
              <FilterSelect
                label="Representative"
                placeholder="All representatives"
                value={representative}
                onChange={setRepresentative}
                load={loadAnyRepresentativeOptions}
              />
            </div>
          )}
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              className="accent-blue-600"
              checked={unassigned}
              onChange={(e) => setUnassigned(e.target.checked)}
            />
            Unassigned only
          </label>
        </>
      }
    />
  );
}

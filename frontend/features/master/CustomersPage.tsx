"use client";

import { Badge } from "@/components/ui/Feedback";
import { Checkbox, TextAreaField, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { useEntityForm } from "@/hooks/useEntityForm";
import { customerService } from "@/services/masterDataService";
import type { Customer, CustomerInput } from "@/types/master";
import { EntityListPage, type FormRenderProps } from "./EntityListPage";

function CustomerForm({ record, onClose, onSaved }: FormRenderProps<Customer>) {
  const form = useEntityForm<CustomerInput, Customer>({
    initial: {
      name: record?.name ?? "",
      phone: record?.phone ?? "",
      email: record?.email ?? "",
      address: record?.address ?? "",
      active: record?.active ?? true,
      version: record?.version,
    },
    validate: (v) => (v.name.trim() ? {} : { name: "Name is required" }),
    save: (v) => (record ? customerService.update(record.id, v) : customerService.create(v)),
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
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Phone" type="tel" value={v.phone} onChange={(e) => set("phone", e.target.value)} error={errors.phone} />
        <TextField label="Email" type="email" value={v.email} onChange={(e) => set("email", e.target.value)} error={errors.email} />
      </div>
      <TextAreaField label="Address" value={v.address} onChange={(e) => set("address", e.target.value)} error={errors.address} />
      {record?.walkIn ? (
        <p className="text-xs text-slate-500">The walk-in customer is the default for counter sales and is always active.</p>
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
  return (
    <EntityListPage<Customer>
      title="Customers"
      description="People and businesses you sell to."
      noun="customer"
      list={customerService.list}
      remove={customerService.remove}
      columns={[
        {
          header: "Name",
          sortField: "name",
          render: (c) => (
            <span className="font-medium text-slate-900">
              {c.name} {c.walkIn && <Badge tone="blue">Default</Badge>}
            </span>
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
    />
  );
}

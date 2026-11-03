"use client";

import { Checkbox, TextAreaField, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { useEntityForm } from "@/hooks/useEntityForm";
import { customerService } from "@/services/masterDataService";
import type { Customer, CustomerInput } from "@/types/master";

interface CustomerFormDialogProps {
  record: Customer | null;
  onClose: () => void;
  onSaved: (customer: Customer, created: boolean) => void;
}

/** Create/edit dialog for a customer; also opened inline from the order form. */
export function CustomerFormDialog({ record, onClose, onSaved }: CustomerFormDialogProps) {
  const form = useEntityForm<CustomerInput, Customer>({
    initial: {
      name: record?.name ?? "",
      phone: record?.phone ?? "",
      email: record?.email ?? "",
      address: record?.address ?? "",
      active: record?.active ?? true,
      version: record?.version,
    },
    validate: (v) => ({ name: v.name.trim() ? "" : "Name is required" }),
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
      <Checkbox
        label="Active"
        description="Inactive customers cannot be chosen for new orders."
        checked={v.active}
        onChange={(e) => set("active", e.target.checked)}
      />
    </FormDialog>
  );
}

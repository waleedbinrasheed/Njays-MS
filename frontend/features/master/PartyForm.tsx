"use client";

import { Checkbox, TextAreaField, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { useEntityForm } from "@/hooks/useEntityForm";
import type { CrudService } from "@/services/masterDataService";
import type { Party, PartyInput } from "@/types/master";
import type { FormRenderProps } from "./EntityListPage";

interface PartyFormProps extends FormRenderProps<Party> {
  noun: "publisher" | "supplier";
  service: CrudService<Party, PartyInput>;
}

/** Create/edit form shared by publishers and suppliers. */
export function PartyForm({ record, onClose, onSaved, noun, service }: PartyFormProps) {
  const form = useEntityForm<PartyInput, Party>({
    initial: {
      name: record?.name ?? "",
      contactPerson: record?.contactPerson ?? "",
      phone: record?.phone ?? "",
      email: record?.email ?? "",
      address: record?.address ?? "",
      active: record?.active ?? true,
      version: record?.version,
    },
    validate: (v) => (v.name.trim() ? {} : { name: "Name is required" }),
    save: (v) => (record ? service.update(record.id, v) : service.create(v)),
    onSaved: (saved) => onSaved(saved, !record),
  });
  const { values: v, set, errors } = form;

  return (
    <FormDialog
      title={record ? `Edit ${noun}` : `New ${noun}`}
      submitLabel={record ? "Save changes" : `Create ${noun}`}
      saving={form.saving}
      formError={form.formError}
      onSubmit={form.submit}
      onClose={onClose}
    >
      <TextField label="Name" value={v.name} onChange={(e) => set("name", e.target.value)} error={errors.name} required autoFocus />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Contact person"
          value={v.contactPerson}
          onChange={(e) => set("contactPerson", e.target.value)}
          error={errors.contactPerson}
        />
        <TextField label="Phone" type="tel" value={v.phone} onChange={(e) => set("phone", e.target.value)} error={errors.phone} />
      </div>
      <TextField label="Email" type="email" value={v.email} onChange={(e) => set("email", e.target.value)} error={errors.email} />
      <TextAreaField label="Address" value={v.address} onChange={(e) => set("address", e.target.value)} error={errors.address} />
      <Checkbox
        label="Active"
        description={`Inactive ${noun}s stay on existing records but cannot be chosen for new ones.`}
        checked={v.active}
        onChange={(e) => set("active", e.target.checked)}
      />
    </FormDialog>
  );
}

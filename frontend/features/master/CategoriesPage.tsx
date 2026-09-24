"use client";

import { Checkbox, TextAreaField, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { useEntityForm } from "@/hooks/useEntityForm";
import { categoryService } from "@/services/masterDataService";
import type { Category, CategoryInput } from "@/types/master";
import { EntityListPage, type FormRenderProps } from "./EntityListPage";

function CategoryForm({ record, onClose, onSaved }: FormRenderProps<Category>) {
  const form = useEntityForm<CategoryInput, Category>({
    initial: {
      name: record?.name ?? "",
      description: record?.description ?? "",
      active: record?.active ?? true,
      version: record?.version,
    },
    validate: (v) => (v.name.trim() ? {} : { name: "Name is required" }),
    save: (v) => (record ? categoryService.update(record.id, v) : categoryService.create(v)),
    onSaved: (saved) => onSaved(saved, !record),
  });
  const { values: v, set, errors } = form;

  return (
    <FormDialog
      title={record ? "Edit category" : "New category"}
      submitLabel={record ? "Save changes" : "Create category"}
      saving={form.saving}
      formError={form.formError}
      onSubmit={form.submit}
      onClose={onClose}
    >
      <TextField label="Name" value={v.name} onChange={(e) => set("name", e.target.value)} error={errors.name} required autoFocus />
      <TextAreaField
        label="Description"
        value={v.description}
        onChange={(e) => set("description", e.target.value)}
        error={errors.description}
      />
      <Checkbox
        label="Active"
        description="Inactive categories cannot be chosen for new products."
        checked={v.active}
        onChange={(e) => set("active", e.target.checked)}
      />
    </FormDialog>
  );
}

export function CategoriesPage() {
  return (
    <EntityListPage<Category>
      title="Categories"
      description="Groupings used to organise products and filter reports."
      noun="category"
      list={categoryService.list}
      remove={categoryService.remove}
      columns={[
        { header: "Name", sortField: "name", render: (c) => <span className="font-medium text-slate-900">{c.name}</span> },
        { header: "Description", render: (c) => <span className="text-slate-600">{c.description ?? "-"}</span> },
      ]}
      defaultSort={{ field: "name", direction: "asc" }}
      searchPlaceholder="Search categories"
      displayName={(c) => c.name}
      renderForm={(props) => <CategoryForm {...props} />}
    />
  );
}

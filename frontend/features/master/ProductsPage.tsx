"use client";

import { useState } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Checkbox, TextField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { useEntityForm } from "@/hooks/useEntityForm";
import { productService } from "@/services/masterDataService";
import type { Product, ProductInput } from "@/types/master";
import { formatCurrency } from "@/utils/format";
import { EntityListPage, type FormRenderProps } from "./EntityListPage";
import { loadCategoryOptions, loadPublisherOptions } from "./lookups";

interface ProductFormValues extends Omit<ProductInput, "publisherId" | "categoryId" | "purchasePrice" | "sellingPrice" | "publicationYear"> {
  publisher: Option | null;
  category: Option | null;
  purchasePrice: string;
  sellingPrice: string;
  publicationYear: string;
}

function toNumber(value: string): number | null {
  return value.trim() === "" ? null : Number(value);
}

function ProductForm({ record, onClose, onSaved }: FormRenderProps<Product>) {
  const form = useEntityForm<ProductFormValues, Product>({
    initial: {
      isbn: record?.isbn ?? "",
      title: record?.title ?? "",
      author: record?.author ?? "",
      publisher: record?.publisher ? { id: record.publisher.id, label: record.publisher.name } : null,
      category: record?.category ? { id: record.category.id, label: record.category.name } : null,
      edition: record?.edition ?? "",
      publicationYear: record?.publicationYear?.toString() ?? "",
      purchasePrice: record?.purchasePrice?.toString() ?? "",
      sellingPrice: record?.sellingPrice?.toString() ?? "",
      active: record?.active ?? true,
      version: record?.version,
    },
    validate: (v) => {
      const e: Record<string, string> = {};
      if (!v.title.trim()) e.title = "Title is required";
      if (!v.purchasePrice.trim()) e.purchasePrice = "Purchase price is required";
      if (!v.sellingPrice.trim()) e.sellingPrice = "Selling price is required";
      return e;
    },
    save: (v) => {
      const input: ProductInput = {
        isbn: v.isbn,
        title: v.title,
        author: v.author,
        publisherId: v.publisher?.id ?? null,
        categoryId: v.category?.id ?? null,
        edition: v.edition,
        publicationYear: toNumber(v.publicationYear),
        purchasePrice: toNumber(v.purchasePrice),
        sellingPrice: toNumber(v.sellingPrice),
        active: v.active,
        version: v.version,
      };
      return record ? productService.update(record.id, input) : productService.create(input);
    },
    onSaved: (saved) => onSaved(saved, !record),
  });
  const { values: v, set, errors } = form;

  return (
    <FormDialog
      size="lg"
      title={record ? "Edit product" : "New product"}
      submitLabel={record ? "Save changes" : "Create product"}
      saving={form.saving}
      formError={form.formError}
      onSubmit={form.submit}
      onClose={onClose}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <TextField label="Title" value={v.title} onChange={(e) => set("title", e.target.value)} error={errors.title} required autoFocus />
        </div>
        <TextField
          label="ISBN"
          value={v.isbn}
          onChange={(e) => set("isbn", e.target.value)}
          error={errors.isbn}
          hint="10 or 13 digits; hyphens are fine"
          inputMode="numeric"
        />
        <TextField label="Author" value={v.author} onChange={(e) => set("author", e.target.value)} error={errors.author} />
        <AsyncSelect label="Publisher" value={v.publisher} onChange={(o) => set("publisher", o)} load={loadPublisherOptions} error={errors.publisherId} />
        <AsyncSelect label="Category" value={v.category} onChange={(o) => set("category", o)} load={loadCategoryOptions} error={errors.categoryId} />
        <TextField label="Edition" value={v.edition} onChange={(e) => set("edition", e.target.value)} error={errors.edition} />
        <TextField
          label="Publication year"
          type="number"
          inputMode="numeric"
          value={v.publicationYear}
          onChange={(e) => set("publicationYear", e.target.value)}
          error={errors.publicationYear}
        />
        <TextField
          label="Purchase price (PKR)"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0"
          value={v.purchasePrice}
          onChange={(e) => set("purchasePrice", e.target.value)}
          error={errors.purchasePrice}
          required
        />
        <TextField
          label="Selling price (PKR)"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0.01"
          value={v.sellingPrice}
          onChange={(e) => set("sellingPrice", e.target.value)}
          error={errors.sellingPrice}
          required
        />
      </div>
      <Checkbox
        label="Active"
        description="Inactive products cannot be purchased or sold."
        checked={v.active}
        onChange={(e) => set("active", e.target.checked)}
      />
    </FormDialog>
  );
}

export function ProductsPage() {
  const [category, setCategory] = useState<Option | null>(null);
  const [publisher, setPublisher] = useState<Option | null>(null);

  return (
    <EntityListPage<Product>
      title="Books/Products"
      description="Everything you buy and sell. Stock levels are managed under Inventory."
      noun="product"
      list={productService.list}
      remove={productService.remove}
      columns={[
        {
          header: "Title",
          sortField: "title",
          render: (p) => (
            <>
              <div className="font-medium text-slate-900">{p.title}</div>
              {p.edition && <div className="text-xs text-slate-500">{p.edition}</div>}
            </>
          ),
        },
        { header: "ISBN", sortField: "isbn", render: (p) => <span className="font-mono text-xs">{p.isbn ?? "-"}</span> },
        { header: "Author", sortField: "author", render: (p) => p.author ?? "-" },
        { header: "Publisher", render: (p) => p.publisher?.name ?? "-" },
        { header: "Category", render: (p) => p.category?.name ?? "-" },
        {
          header: "Purchase",
          sortField: "purchasePrice",
          className: "text-right tabular-nums whitespace-nowrap",
          render: (p) => formatCurrency(p.purchasePrice),
        },
        {
          header: "Selling",
          sortField: "sellingPrice",
          className: "text-right tabular-nums whitespace-nowrap",
          render: (p) => formatCurrency(p.sellingPrice),
        },
      ]}
      defaultSort={{ field: "title", direction: "asc" }}
      searchPlaceholder="Search ISBN, title or author"
      displayName={(p) => p.title}
      renderForm={(props) => <ProductForm {...props} />}
      filterQuery={{ categoryId: category?.id, publisherId: publisher?.id }}
      filters={
        <>
          <div className="sm:w-56">
            <FilterSelect label="Category" placeholder="All categories" value={category} onChange={setCategory} load={loadCategoryOptions} />
          </div>
          <div className="sm:w-56">
            <FilterSelect label="Publisher" placeholder="All publishers" value={publisher} onChange={setPublisher} load={loadPublisherOptions} />
          </div>
        </>
      }
    />
  );
}

/** Compact picker for the filter bar (label visually hidden). */
function FilterSelect({ placeholder, ...props }: {
  label: string;
  placeholder: string;
  value: Option | null;
  onChange: (o: Option | null) => void;
  load: (s: string, signal: AbortSignal) => Promise<Option[]>;
}) {
  return (
    <div className="[&_label]:sr-only">
      <AsyncSelect {...props} placeholder={placeholder} />
    </div>
  );
}

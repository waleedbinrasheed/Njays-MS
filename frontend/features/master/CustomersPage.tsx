"use client";

import Link from "next/link";
import { customerService } from "@/services/masterDataService";
import type { Customer } from "@/types/master";
import { EntityListPage, type Column } from "./EntityListPage";
import { CustomerFormDialog } from "./CustomerFormDialog";

const COLUMNS: Column<Customer>[] = [
  {
    header: "Name",
    sortField: "name",
    render: (c) => (
      <Link href={`/master/customers/${c.id}`} className="font-medium text-blue-700 hover:underline">
        {c.name}
      </Link>
    ),
  },
  {
    header: "Phone / email",
    render: (c) => (
      <>
        <div>{c.phone ?? "-"}</div>
        {c.email && <div className="text-xs text-slate-500">{c.email}</div>}
      </>
    ),
  },
  { header: "Address", render: (c) => <span className="line-clamp-2 max-w-xs whitespace-pre-line">{c.address ?? "-"}</span> },
  { header: "Orders", className: "text-right tabular-nums", render: (c) => c.orderCount },
];

export function CustomersPage() {
  return (
    <EntityListPage<Customer>
      title="Customers"
      description="Customers who place orders, with their measurement records."
      noun="customer"
      list={customerService.list}
      remove={customerService.remove}
      columns={COLUMNS}
      defaultSort={{ field: "name", direction: "asc" }}
      searchPlaceholder="Search name, phone or email"
      displayName={(c) => c.name}
      canDelete={(c) => c.orderCount === 0}
      renderForm={({ record, onClose, onSaved }) => <CustomerFormDialog record={record} onClose={onClose} onSaved={onSaved} />}
    />
  );
}

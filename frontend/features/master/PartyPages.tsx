"use client";

import { publisherService, supplierService } from "@/services/masterDataService";
import type { Party } from "@/types/master";
import { EntityListPage, type Column } from "./EntityListPage";
import { PartyForm } from "./PartyForm";

const COLUMNS: Column<Party>[] = [
  { header: "Name", sortField: "name", render: (p) => <span className="font-medium text-slate-900">{p.name}</span> },
  { header: "Contact person", sortField: "contactPerson", render: (p) => p.contactPerson ?? "-" },
  {
    header: "Phone / email",
    render: (p) => (
      <>
        <div>{p.phone ?? "-"}</div>
        {p.email && <div className="text-xs text-slate-500">{p.email}</div>}
      </>
    ),
  },
];

export function PublishersPage() {
  return (
    <EntityListPage<Party>
      title="Publishers"
      description="Publishing houses of the books you sell."
      noun="publisher"
      list={publisherService.list}
      remove={publisherService.remove}
      columns={COLUMNS}
      defaultSort={{ field: "name", direction: "asc" }}
      searchPlaceholder="Search name, contact, phone or email"
      displayName={(p) => p.name}
      renderForm={(props) => <PartyForm {...props} noun="publisher" service={publisherService} />}
    />
  );
}

export function SuppliersPage() {
  return (
    <EntityListPage<Party>
      title="Suppliers"
      description="Businesses you purchase stock from."
      noun="supplier"
      list={supplierService.list}
      remove={supplierService.remove}
      columns={COLUMNS}
      defaultSort={{ field: "name", direction: "asc" }}
      searchPlaceholder="Search name, contact, phone or email"
      displayName={(p) => p.name}
      renderForm={(props) => <PartyForm {...props} noun="supplier" service={supplierService} />}
    />
  );
}

"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { Badge, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { FormDialog } from "@/components/ui/FormDialog";
import { Pagination } from "@/components/ui/Pagination";
import { PageSpinner, Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { DocumentStatusBadge, PaymentStatusBadge } from "@/features/documents/StatusBadges";
import { useApiQuery } from "@/hooks/useApiQuery";
import { ApiError } from "@/services/apiClient";
import { customerService, representativeService } from "@/services/masterDataService";
import { saleService } from "@/services/saleService";
import type { Representative } from "@/types/master";
import { formatCurrency, formatDate } from "@/utils/format";
import { loadRepresentativeOptions } from "./lookups";
import { RepresentativeForm } from "./RepresentativesPage";

export function RepresentativeDetailPage({ id }: { id: number }) {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canManage = hasPermission("MASTER_DATA_MANAGE");
  const canSeeSales = hasPermission("SALE_VIEW");
  const [version, setVersion] = useState(0);
  const [customerPage, setCustomerPage] = useState(0);
  const [editing, setEditing] = useState(false);
  const [moving, setMoving] = useState(false);

  const rep = useApiQuery(() => representativeService.get(id), [id, version]);
  const customers = useApiQuery(
    (signal) => customerService.list({ representativeId: id, size: 20, page: customerPage, sort: "name,asc" }, signal),
    [id, customerPage, version],
  );
  const sales = useApiQuery(
    (signal) => (canSeeSales ? saleService.list({ representativeId: id, size: 10 }, signal) : Promise.resolve(null)),
    [id, canSeeSales, version],
  );

  if (rep.error && !rep.data) return <ErrorBanner message={rep.error} onRetry={rep.reload} />;
  if (!rep.data) return <PageSpinner />;
  const r = rep.data;

  return (
    <>
      <Link href="/master/representatives" className="mb-3 inline-block text-sm text-slate-600 hover:text-slate-900">
        ← Representatives
      </Link>
      <PageHeader
        title={r.name}
        description={[r.area, r.phone, r.email].filter(Boolean).join(" · ") || "No contact details"}
        actions={
          <>
            {!r.active && <Badge tone="red">Inactive</Badge>}
            {canManage && (
              <Button variant="secondary" onClick={() => setEditing(true)}>
                Edit
              </Button>
            )}
            {canManage && r.customerCount > 0 && <Button onClick={() => setMoving(true)}>Move all customers to…</Button>}
          </>
        }
      />

      <div className="grid gap-5 xl:grid-cols-2">
        <section className="rounded-lg border border-slate-200 bg-white">
          <header className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-900">Customers ({r.customerCount})</h2>
            {customers.loading && customers.data && <Spinner size="sm" />}
          </header>
          {customers.data && customers.data.content.length === 0 ? (
            <EmptyState title="No customers yet" description="Assign customers on the Customers screen." />
          ) : (
            <ul className="divide-y divide-slate-100 px-4 text-sm">
              {(customers.data?.content ?? []).map((c) => (
                <li key={c.id} className="flex items-center justify-between gap-3 py-2">
                  <span className="text-slate-900">{c.name}</span>
                  <span className="text-xs text-slate-500">
                    {[c.phone, c.active ? null : "Inactive"].filter(Boolean).join(" · ")}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {customers.data && <Pagination {...customers.data} onPageChange={setCustomerPage} />}
        </section>

        {canSeeSales && (
          <section className="rounded-lg border border-slate-200 bg-white">
            <header className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
              <h2 className="text-sm font-semibold text-slate-900">Recent sales</h2>
              {hasPermission("REPORT_VIEW") && (
                <Link href="/reports/representatives" className="text-sm text-blue-700 hover:underline">
                  Representative report
                </Link>
              )}
            </header>
            {sales.data && sales.data.content.length === 0 ? (
              <EmptyState title="No sales yet" />
            ) : (
              <ul className="divide-y divide-slate-100 px-4 text-sm">
                {(sales.data?.content ?? []).map((s) => (
                  <li key={s.id} className="flex items-center justify-between gap-3 py-2">
                    <div>
                      <Link href={`/sales/${s.id}`} className="font-mono text-xs font-medium text-blue-700 hover:underline">
                        {s.invoiceNumber}
                      </Link>{" "}
                      <DocumentStatusBadge status={s.status} />
                      <p className="text-xs text-slate-500">
                        {formatDate(s.saleDate)}
                        {s.customer ? ` · for ${s.customer.name}` : ""}
                      </p>
                    </div>
                    <div className="text-right">
                      <div className="tabular-nums">{formatCurrency(s.total)}</div>
                      <PaymentStatusBadge status={s.paymentStatus} />
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </div>

      {editing && (
        <RepresentativeForm
          record={r}
          onClose={() => setEditing(false)}
          onSaved={(saved) => {
            setEditing(false);
            toast.success(`Representative "${saved.name}" saved`);
            setVersion((v) => v + 1);
          }}
        />
      )}
      {moving && (
        <MoveCustomersDialog
          from={r}
          onClose={() => setMoving(false)}
          onDone={(moved, toName) => {
            setMoving(false);
            toast.success(`${moved} customer(s) moved to ${toName}`);
            setVersion((v) => v + 1);
          }}
        />
      )}
    </>
  );
}

function MoveCustomersDialog({
  from,
  onClose,
  onDone,
}: {
  from: Representative;
  onClose: () => void;
  onDone: (moved: number, toName: string) => void;
}) {
  const [target, setTarget] = useState<Option | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!target) {
      setError("Choose the representative to move the customers to");
      return;
    }
    if (target.id === from.id) {
      setError("Choose a different representative");
      return;
    }
    setSaving(true);
    try {
      const result = await representativeService.moveCustomers(from.id, target.id);
      onDone(result.moved, result.to.name);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not move the customers");
      setSaving(false);
    }
  }

  return (
    <FormDialog
      title={`Move all customers of ${from.name}`}
      submitLabel={`Move ${from.customerCount} customer(s)`}
      saving={saving}
      formError={error}
      onSubmit={submit}
      onClose={onClose}
    >
      <p className="text-sm text-slate-600">
        All {from.customerCount} customer(s) will be assigned to the representative you choose. Past invoices are not changed.
      </p>
      <AsyncSelect label="Move to" value={target} onChange={setTarget} load={loadRepresentativeOptions} required />
    </FormDialog>
  );
}

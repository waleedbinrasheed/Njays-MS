"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { PageSpinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { DressMeasurements } from "@/components/measurements/DressMeasurements";
import { useAuth } from "@/features/auth/AuthProvider";
import { OrderStatusBadge, PaymentStatusBadge } from "@/features/orders/OrderBadges";
import { useApiQuery } from "@/hooks/useApiQuery";
import { customerService, measurementService } from "@/services/masterDataService";
import { orderService } from "@/services/orderService";
import { formatCurrency, formatDate } from "@/utils/format";
import { CustomerFormDialog } from "./CustomerFormDialog";
import { TakeMeasurementsDialog } from "./TakeMeasurementsDialog";

export function CustomerDetailPage({ id }: { id: number }) {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canManageMaster = hasPermission("MASTER_DATA_MANAGE");
  const canOrder = hasPermission("ORDER_MANAGE");

  const customer = useApiQuery(() => customerService.get(id), [id]);
  const orders = useApiQuery((signal) => orderService.list({ customerId: id, page: 0, size: 50, sort: "dueDate,desc" }, signal), [id]);
  const records = useApiQuery((signal) => measurementService.forCustomer(id, signal), [id]);
  const [editing, setEditing] = useState(false);
  const [measuring, setMeasuring] = useState(false);

  if (customer.error && !customer.data) return <ErrorBanner message={customer.error} onRetry={customer.reload} />;
  if (!customer.data) return <PageSpinner />;
  const c = customer.data;

  return (
    <>
      <PageHeader
        title={c.name}
        description={c.active ? "Customer" : "Inactive customer"}
        actions={
          <>
            <Link href="/master/customers" className="inline-flex h-9 items-center rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50">
              All customers
            </Link>
            {canManageMaster && (
              <Button variant="secondary" onClick={() => setEditing(true)}>
                Edit customer
              </Button>
            )}
            {canOrder && (
              <Link href="/orders/new" className="inline-flex h-9 items-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700">
                New order
              </Link>
            )}
          </>
        }
      />

      <section className="mb-5 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 text-sm sm:grid-cols-3">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Phone</p>
          <p className="text-slate-900">{c.phone ?? "-"}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Email</p>
          <p className="text-slate-900">{c.email ?? "-"}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Address</p>
          <p className="whitespace-pre-line text-slate-900">{c.address ?? "-"}</p>
        </div>
      </section>

      <section className="mb-6 overflow-hidden rounded-lg border border-slate-200 bg-white">
        <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">Orders</h2>
        {orders.error && (
          <div className="p-4">
            <ErrorBanner message={orders.error} onRetry={orders.reload} />
          </div>
        )}
        {orders.data && orders.data.content.length === 0 && <EmptyState title="No orders yet" />}
        {orders.data && orders.data.content.length > 0 && (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-2.5 font-medium">Order no</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Due date</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Status</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Total</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Balance</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Payment</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {orders.data.content.map((o) => (
                  <tr key={o.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2.5">
                      <Link href={`/orders/${o.id}`} className="font-medium text-blue-700 hover:underline">
                        {o.orderNumber}
                      </Link>
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5">{formatDate(o.dueDate)}</td>
                    <td className="px-4 py-2.5">
                      <OrderStatusBadge status={o.status} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{formatCurrency(o.total)}</td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">{formatCurrency(o.balance)}</td>
                    <td className="px-4 py-2.5">
                      <PaymentStatusBadge status={o.paymentStatus} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-slate-900">Measurement records</h2>
          {canOrder && (
            <Button onClick={() => setMeasuring(true)}>Take measurements</Button>
          )}
        </div>
        {records.error && <ErrorBanner message={records.error} onRetry={records.reload} />}
        {records.data && records.data.length === 0 && (
          <EmptyState title="No measurements taken yet" description="Take measurements for a dress type before creating an order that needs them." />
        )}
        {(records.data ?? []).map((r) => (
          <article key={r.id} className="rounded-lg border border-slate-200 bg-white p-4">
            <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
              <h3 className="text-sm font-semibold text-slate-900">{r.garmentType.name}</h3>
              <p className="text-xs text-slate-500">
                Taken {formatDate(r.takenOn)} by {r.createdBy}
                {r.notes ? ` · ${r.notes}` : ""}
              </p>
            </div>
            <DressMeasurements code={r.garmentType.code} dressName={r.garmentType.name} measurements={r.values} />
          </article>
        ))}
      </section>

      {editing && (
        <CustomerFormDialog
          record={c}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            toast.success(`Customer "${c.name}" saved`);
            customer.reload();
          }}
        />
      )}
      {measuring && (
        <TakeMeasurementsDialog
          customerId={c.id}
          customerName={c.name}
          onClose={() => setMeasuring(false)}
          onSaved={(garment) => {
            setMeasuring(false);
            toast.success(`${garment} measurements saved`);
            records.reload();
          }}
        />
      )}
    </>
  );
}

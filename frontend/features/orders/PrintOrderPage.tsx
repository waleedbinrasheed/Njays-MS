"use client";

import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/Feedback";
import { PageSpinner } from "@/components/ui/Spinner";
import { printTwoCopies, TwoCopyPrint } from "@/features/documents/TwoCopyPrint";
import { useApiQuery } from "@/hooks/useApiQuery";
import { orderService } from "@/services/orderService";
import { settingsService } from "@/services/settingsService";
import { OrderInvoice } from "./OrderInvoice";

export function PrintOrderPage({ id }: { id: number }) {
  const order = useApiQuery((signal) => orderService.get(id, signal), [id]);
  const business = useApiQuery((signal) => settingsService.business(signal), []);

  if (order.error && !order.data) return <ErrorBanner message={order.error} onRetry={order.reload} />;
  if (!order.data) return <PageSpinner />;
  const o = order.data;
  const b = business.data ?? null;

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Link href={`/orders/${o.id}`} className="text-sm text-blue-700 hover:underline">
          Back to order {o.orderNumber}
        </Link>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => window.print()}>
            Print one copy
          </Button>
          <Button onClick={printTwoCopies}>Print 2 copies</Button>
        </div>
      </div>

      <div className="print-single rounded-lg border border-slate-200 bg-white p-6 print:border-0 print:p-0 sm:p-8">
        <OrderInvoice order={o} business={b} />
      </div>

      <TwoCopyPrint renderCopy={(copyLabel) => <OrderInvoice order={o} business={b} copyLabel={copyLabel} compact />} />
    </>
  );
}

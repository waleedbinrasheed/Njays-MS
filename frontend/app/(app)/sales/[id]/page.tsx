"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { SaleDetailPage } from "@/features/sales/SaleDetailPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const saleId = Number(id);
  return (
    <RequirePermission permission="SALE_VIEW">
      {Number.isInteger(saleId) && saleId > 0 ? (
        <SaleDetailPage key={saleId} id={saleId} />
      ) : (
        <EmptyState title="Invoice not found" />
      )}
    </RequirePermission>
  );
}

"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { PurchaseDetailPage } from "@/features/purchases/PurchaseDetailPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const purchaseId = Number(id);
  return (
    <RequirePermission permission="PURCHASE_VIEW">
      {Number.isInteger(purchaseId) && purchaseId > 0 ? (
        <PurchaseDetailPage key={purchaseId} id={purchaseId} />
      ) : (
        <EmptyState title="Purchase not found" />
      )}
    </RequirePermission>
  );
}

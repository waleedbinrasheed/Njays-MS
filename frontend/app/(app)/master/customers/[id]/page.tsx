"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { CustomerDetailPage } from "@/features/master/CustomerDetailPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const customerId = Number(id);
  return (
    <RequirePermission permission="MASTER_DATA_VIEW">
      {Number.isInteger(customerId) && customerId > 0 ? <CustomerDetailPage key={customerId} id={customerId} /> : <EmptyState title="Customer not found" />}
    </RequirePermission>
  );
}

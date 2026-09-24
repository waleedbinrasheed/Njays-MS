"use client";

import { useParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Feedback";
import { RequirePermission } from "@/features/auth/AuthGuard";
import { ReturnDetailPage } from "@/features/returns/ReturnDetailPage";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  const n = Number(id);
  return (
    <RequirePermission permission="SALE_VIEW">
      {Number.isInteger(n) && n > 0 ? <ReturnDetailPage key={n} kind="sale" id={n} /> : <EmptyState title="Not found" />}
    </RequirePermission>
  );
}

"use client";

import { PageHeader } from "@/components/ui/Feedback";
import { useAuth } from "@/features/auth/AuthProvider";
import { BackendStatus } from "@/features/system/BackendStatus";

export default function DashboardPage() {
  const { user } = useAuth();
  return (
    <>
      <PageHeader title="Dashboard" description={`Welcome, ${user?.fullName ?? ""}`} />
      <section className="rounded-lg border border-slate-200 bg-white p-6">
        <p className="text-sm text-slate-600">
          Sales, purchases and stock figures will appear here once those modules are built.
        </p>
        <div className="mt-4">
          <BackendStatus />
        </div>
      </section>
    </>
  );
}

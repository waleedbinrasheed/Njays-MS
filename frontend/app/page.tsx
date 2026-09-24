import { BackendStatus } from "@/features/system/BackendStatus";

export default function Home() {
  return (
    <main className="flex flex-1 items-center justify-center p-6">
      <section className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">Inventory Management System</h1>
        <p className="mt-1 text-sm text-slate-500">Project setup complete. Sign-in arrives in Phase 2.</p>
        <div className="mt-6">
          <BackendStatus />
        </div>
      </section>
    </main>
  );
}

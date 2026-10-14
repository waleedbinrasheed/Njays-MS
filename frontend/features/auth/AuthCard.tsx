import type { ReactNode } from "react";
import { BrandLogo } from "@/components/brand/Brand";

export function AuthCard({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-10">
      <section className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
        <div className="mb-6 flex flex-col items-center text-center">
          <BrandLogo className="h-28 w-auto" />
          <h1 className="mt-4 text-xl font-semibold text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
        </div>
        {children}
      </section>
    </main>
  );
}

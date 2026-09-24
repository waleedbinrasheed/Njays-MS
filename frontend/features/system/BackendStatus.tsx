"use client";

import { useBackendHealth } from "@/hooks/useBackendHealth";

const LABELS = {
  checking: { text: "Checking server...", dot: "bg-slate-400" },
  up: { text: "Server connected", dot: "bg-emerald-500" },
  down: { text: "Server unavailable", dot: "bg-red-500" },
} as const;

export function BackendStatus() {
  const state = useBackendHealth();
  const { text, dot } = LABELS[state];
  return (
    <p className="inline-flex items-center gap-2 text-sm text-slate-600" role="status">
      <span className={`h-2.5 w-2.5 rounded-full ${dot}`} aria-hidden />
      {text}
    </p>
  );
}

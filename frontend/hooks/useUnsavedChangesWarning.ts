"use client";

import { useEffect } from "react";

/** Asks the browser to confirm before closing/reloading the tab while a form has unsaved work. */
export function useUnsavedChangesWarning(active: boolean) {
  useEffect(() => {
    if (!active) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [active]);
}

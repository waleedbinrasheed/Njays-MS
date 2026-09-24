"use client";

import { useEffect, useState } from "react";
import { systemService } from "@/services/systemService";

export type BackendState = "checking" | "up" | "down";

export function useBackendHealth(): BackendState {
  const [state, setState] = useState<BackendState>("checking");

  useEffect(() => {
    const controller = new AbortController();
    systemService
      .health(controller.signal)
      .then((h) => setState(h.status === "UP" ? "up" : "down"))
      .catch((err: unknown) => {
        if (!(err instanceof DOMException && err.name === "AbortError")) setState("down");
      });
    return () => controller.abort();
  }, []);

  return state;
}

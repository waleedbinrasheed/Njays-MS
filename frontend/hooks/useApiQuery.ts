"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/services/apiClient";

interface QueryState<T> {
  data: T | undefined;
  loading: boolean;
  error: string | null;
  reload: () => void;
}

interface Settled<T> {
  key: string;
  data: T | undefined;
  error: string | null;
}

/**
 * Runs `fetcher` whenever `deps` change, cancelling the previous request.
 * Keeps the last data while reloading so tables do not flash empty.
 * `deps` must be JSON-serialisable (primitives and plain objects).
 */
export function useApiQuery<T>(fetcher: (signal: AbortSignal) => Promise<T>, deps: unknown[]): QueryState<T> {
  const [version, setVersion] = useState(0);
  const [settled, setSettled] = useState<Settled<T>>({ key: "", data: undefined, error: null });
  const fetcherRef = useRef(fetcher);
  const key = JSON.stringify([...deps, version]);

  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  useEffect(() => {
    const controller = new AbortController();
    fetcherRef
      .current(controller.signal)
      .then((data) => setSettled({ key, data, error: null }))
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        const message = err instanceof ApiError ? err.message : "Something went wrong. Please try again.";
        setSettled((prev) => ({ key, data: prev.data, error: message }));
      });
    return () => controller.abort();
  }, [key]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return { data: settled.data, loading: settled.key !== key, error: settled.error, reload };
}

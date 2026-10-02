import type { ApiErrorBody } from "@/types/api";

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.status = body.status;
    this.fieldErrors = Object.fromEntries((body.errors ?? []).map((e) => [e.field, e.message]));
  }
}

let onUnauthorized: (() => void) | null = null;
let onActivity: (() => void) | null = null;

/** Registered by the auth layer so an expired session anywhere sends the user to login. */
export function setUnauthorizedListener(listener: (() => void) | null) {
  onUnauthorized = listener;
}

/** Called after every successful call; each one also renews the server session. */
export function setActivityListener(listener: (() => void) | null) {
  onActivity = listener;
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);
const CSRF_EXPIRED_PREFIX = "Security token";

function readCookie(name: string): string | undefined {
  return document.cookie
    .split("; ")
    .find((c) => c.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

async function csrfToken(forceRefresh = false): Promise<string | undefined> {
  let token = forceRefresh ? undefined : readCookie("XSRF-TOKEN");
  if (!token) {
    // Any GET makes the backend issue a fresh XSRF-TOKEN cookie.
    await fetch("/api/health", { credentials: "same-origin" });
    token = readCookie("XSRF-TOKEN");
  }
  return token ? decodeURIComponent(token) : undefined;
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  signal?: AbortSignal;
  /** A 401 from this call is an expected outcome (e.g. wrong password), not an expired session. */
  skipAuthRedirect?: boolean;
  /**
   * Retry while the server is starting (502/503/504 from the hosting proxy). On by default for reads;
   * only enable it for writes that are safe to repeat, such as login.
   */
  retryWhileStarting?: boolean;
}

/** Hosting proxies answer these while a sleeping server wakes up; the request never reached the app. */
const STARTING_STATUSES = new Set([502, 503, 504]);
const STARTING_RETRY_DELAY_MS = 5_000;
const STARTING_MAX_ATTEMPTS = 48; // about 4 minutes

function wait(ms: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener("abort", () => {
      clearTimeout(timer);
      reject(new DOMException("Aborted", "AbortError"));
    });
  });
}

function buildUrl(path: string, query: RequestOptions["query"]): string {
  let url = `/api${path}`;
  if (query) {
    const params = new URLSearchParams();
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== "") params.append(k, String(v));
    }
    const qs = params.toString();
    if (qs) url += `?${qs}`;
  }
  return url;
}

async function toApiError(response: Response, url: string): Promise<ApiError> {
  try {
    return new ApiError((await response.json()) as ApiErrorBody);
  } catch {
    return new ApiError({
      timestamp: new Date().toISOString(),
      status: response.status,
      message: response.status >= 500 ? "The server is unavailable. Please try again." : "Request failed",
      path: url,
    });
  }
}

/** Single entry point for backend calls: JSON, cookies, CSRF and error mapping. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method ?? "GET").toUpperCase();
  const url = buildUrl(path, options.query);

  const send = async (refreshCsrf: boolean) => {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (options.body !== undefined) headers["Content-Type"] = "application/json";
    if (!SAFE_METHODS.has(method)) {
      const token = await csrfToken(refreshCsrf);
      if (token) headers["X-XSRF-TOKEN"] = token;
    }
    return fetch(url, {
      method,
      headers,
      credentials: "same-origin",
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });
  };

  const retryWhileStarting = options.retryWhileStarting ?? SAFE_METHODS.has(method);

  let response: Response;
  try {
    response = await send(false);
    for (let attempt = 1; retryWhileStarting && STARTING_STATUSES.has(response.status) && attempt < STARTING_MAX_ATTEMPTS; attempt++) {
      await wait(STARTING_RETRY_DELAY_MS, options.signal);
      response = await send(false);
    }
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 0,
      message: "Cannot reach the server. Check your network connection.",
      path: url,
    });
  }

  let error: ApiError | null = response.ok ? null : await toApiError(response, url);

  // A stale CSRF cookie (e.g. right after login/logout) is retried once with a fresh token.
  if (error && error.status === 403 && error.message.startsWith(CSRF_EXPIRED_PREFIX)) {
    response = await send(true);
    error = response.ok ? null : await toApiError(response, url);
  }

  if (error) {
    if (error.status === 401 && !options.skipAuthRedirect) onUnauthorized?.();
    throw error;
  }

  onActivity?.();
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string, query?: RequestOptions["query"], signal?: AbortSignal) =>
    apiRequest<T>(path, { query, signal }),
  post: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: "POST", body }),
  put: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: "PUT", body }),
  delete: <T>(path: string) => apiRequest<T>(path, { method: "DELETE" }),
};

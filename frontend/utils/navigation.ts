/** Only same-site relative paths are accepted as post-login destinations (no open redirects). */
export function safeRedirectPath(value: string | null | undefined, fallback = "/"): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) return fallback;
  if (value.startsWith("/login") || value.startsWith("/change-password")) return fallback;
  return value;
}

export function loginUrl(options: { next?: string; expired?: boolean } = {}): string {
  const params = new URLSearchParams();
  if (options.expired) params.set("expired", "1");
  if (options.next && options.next !== "/") params.set("next", options.next);
  const qs = params.toString();
  return qs ? `/login?${qs}` : "/login";
}

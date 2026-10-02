"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/FormField";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError, api } from "@/services/apiClient";
import { safeRedirectPath } from "@/utils/navigation";

export function LoginForm() {
  const { login, status, user } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const next = safeRedirectPath(params.get("next"));
  const expired = params.get("expired") === "1";

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [slow, setSlow] = useState(false);
  // "waking" while the server has not answered the warm-up request for a few seconds.
  const [waking, setWaking] = useState(false);

  // Wake a sleeping server (free hosting) as soon as the page opens, while the user is still typing.
  useEffect(() => {
    const controller = new AbortController();
    let done = false;
    const timer = setTimeout(() => {
      if (!done) setWaking(true);
    }, 2_000);
    api
      .get("/health", undefined, controller.signal)
      .catch(() => undefined)
      .finally(() => {
        done = true;
        clearTimeout(timer);
        if (!controller.signal.aborted) setWaking(false);
      });
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, []);

  // A sign-in that takes this long means the server is waking up (free hosting); say so.
  useEffect(() => {
    if (!submitting) return;
    const timer = setTimeout(() => setSlow(true), 4_000);
    return () => {
      clearTimeout(timer);
      setSlow(false);
    };
  }, [submitting]);

  // Already signed in (e.g. opened /login in a second tab).
  useEffect(() => {
    if (status === "authenticated" && user && !submitting) {
      router.replace(user.mustChangePassword ? "/change-password" : next);
    }
  }, [status, user, submitting, router, next]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!username.trim() || !password) {
      setError("Enter your username and password");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const me = await login(username.trim(), password);
      router.replace(me.mustChangePassword ? `/change-password?next=${encodeURIComponent(next)}` : next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Sign-in failed. Please try again.");
      setPassword("");
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {expired && !error && (
        <p role="status" className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          Your session expired due to inactivity. Please sign in again.
        </p>
      )}
      {error && (
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}
      {(slow || waking) && !error && (
        <p role="status" className="rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-800">
          {submitting
            ? "Signing in. The server is still waking up after a quiet period; this can take up to three minutes."
            : "Waking up the server after a quiet period (up to three minutes). You can enter your details meanwhile."}
        </p>
      )}
      <TextField
        label="Username or email"
        name="username"
        autoComplete="username"
        autoFocus
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        required
      />
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
      />
      <Button type="submit" className="w-full" loading={submitting}>
        Sign in
      </Button>
    </form>
  );
}

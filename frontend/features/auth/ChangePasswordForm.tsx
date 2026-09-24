"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/FormField";
import { PageSpinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/apiClient";
import { authService } from "@/services/authService";
import { loginUrl, safeRedirectPath } from "@/utils/navigation";

const PASSWORD_HINT = "At least 8 characters, with letters and numbers";

export function ChangePasswordForm() {
  const { status, user, refresh, logout } = useAuth();
  const router = useRouter();
  const toast = useToast();
  const next = safeRedirectPath(useSearchParams().get("next"));

  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (status === "anonymous") router.replace(loginUrl({ next: "/change-password" }));
  }, [status, router]);

  if (status !== "authenticated" || !user) return <PageSpinner />;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!current) local.currentPassword = "Current password is required";
    if (!password) local.newPassword = "New password is required";
    if (password && confirm !== password) local.confirm = "Passwords do not match";
    setErrors(local);
    if (Object.keys(local).length) return;

    setSubmitting(true);
    setFormError(null);
    try {
      await authService.changePassword(current, password);
      await refresh();
      toast.success("Password changed");
      router.replace(next);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        if (!Object.keys(err.fieldErrors).length) setFormError(err.message);
      } else {
        setFormError("Could not change the password. Please try again.");
      }
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {user.mustChangePassword && (
        <p className="rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-800">
          You are using a temporary password. Choose a new one to continue.
        </p>
      )}
      {formError && (
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {formError}
        </p>
      )}
      <TextField
        label="Current password"
        type="password"
        autoComplete="current-password"
        value={current}
        onChange={(e) => setCurrent(e.target.value)}
        error={errors.currentPassword}
        required
      />
      <TextField
        label="New password"
        type="password"
        autoComplete="new-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={errors.newPassword}
        hint={PASSWORD_HINT}
        required
      />
      <TextField
        label="Confirm new password"
        type="password"
        autoComplete="new-password"
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
        error={errors.confirm}
        required
      />
      <div className="flex gap-2">
        <Button type="submit" className="flex-1" loading={submitting}>
          Change password
        </Button>
        {user.mustChangePassword ? (
          <Button variant="secondary" onClick={() => logout()} disabled={submitting}>
            Sign out
          </Button>
        ) : (
          <Button variant="secondary" onClick={() => router.back()} disabled={submitting}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
}

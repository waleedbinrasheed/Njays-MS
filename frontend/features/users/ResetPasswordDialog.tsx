"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { ApiError } from "@/services/apiClient";
import { userService } from "@/services/userService";
import type { User } from "@/types/user";

/** Mount only while open, with key={user.id}. */
interface ResetPasswordDialogProps {
  user: User;
  onClose: () => void;
  onDone: (user: User) => void;
}

export function ResetPasswordDialog({ user, onClose, onDone }: ResetPasswordDialogProps) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!password) {
      setError("Temporary password is required");
      return;
    }
    setSaving(true);
    try {
      await userService.resetPassword(user.id, password);
      onDone(user);
    } catch (err) {
      setError(err instanceof ApiError ? (err.fieldErrors.newPassword ?? err.message) : "Could not reset the password");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      title={`Reset password: ${user.username}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="reset-form" loading={saving}>
            Reset password
          </Button>
        </>
      }
    >
      <form id="reset-form" onSubmit={handleSubmit} className="space-y-3" noValidate>
        <p className="text-sm text-slate-600">
          Set a temporary password and give it to the user. They will be asked to choose a new one when they sign in.
        </p>
        <TextField
          label="Temporary password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={error ?? undefined}
          hint="At least 8 characters with letters and numbers"
          required
        />
      </form>
    </Modal>
  );
}

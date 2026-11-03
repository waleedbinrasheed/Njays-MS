"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { TextAreaField } from "@/components/ui/FormField";
import { FormDialog } from "@/components/ui/FormDialog";
import { ApiError } from "@/services/apiClient";

interface CancelDocumentDialogProps<T> {
  title: string;
  submitLabel: string;
  explanation: ReactNode;
  cancel: (reason: string) => Promise<T>;
  onClose: () => void;
  onDone: (result: T) => void;
}

/** Reason-required cancel confirmation (used for cancelling orders). */
export function CancelDocumentDialog<T>({ title, submitLabel, explanation, cancel, onClose, onDone }: CancelDocumentDialogProps<T>) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!reason.trim()) {
      setError("Give a reason for cancelling");
      return;
    }
    setSaving(true);
    try {
      onDone(await cancel(reason));
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not cancel");
      setSaving(false);
    }
  }

  return (
    <FormDialog title={title} submitLabel={submitLabel} saving={saving} formError={formError} onSubmit={submit} onClose={onClose}>
      <div className="text-sm text-slate-600">{explanation}</div>
      <TextAreaField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} required autoFocus />
    </FormDialog>
  );
}

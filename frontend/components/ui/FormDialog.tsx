"use client";

import type { FormEvent, ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";

interface FormDialogProps {
  title: string;
  submitLabel: string;
  saving: boolean;
  formError: string | null;
  onSubmit: (e: FormEvent) => void;
  onClose: () => void;
  children: ReactNode;
  size?: "md" | "lg";
}

/** Modal + form + footer buttons + top-level error banner, used by all create/edit dialogs. */
export function FormDialog({ title, submitLabel, saving, formError, onSubmit, onClose, children, size }: FormDialogProps) {
  return (
    <Modal
      open
      size={size}
      title={title}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="entity-form" loading={saving}>
            {submitLabel}
          </Button>
        </>
      }
    >
      <form id="entity-form" onSubmit={onSubmit} className="space-y-4" noValidate>
        {formError && (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {formError}
          </p>
        )}
        {children}
      </form>
    </Modal>
  );
}

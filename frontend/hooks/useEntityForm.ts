"use client";

import { useState, type FormEvent } from "react";
import { ApiError } from "@/services/apiClient";

type Errors = Record<string, string>;

interface EntityFormOptions<V, R> {
  initial: V;
  /** Client-side checks for obviously missing values; the backend validates everything again. */
  validate?: (values: V) => Partial<Errors>;
  save: (values: V) => Promise<R>;
  onSaved: (result: R) => void;
}

/** Form state, field errors from the backend, and submit handling for create/edit dialogs. */
export function useEntityForm<V extends object, R>({ initial, validate, save, onSaved }: EntityFormOptions<V, R>) {
  const [values, setValues] = useState<V>(initial);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const set = <K extends keyof V>(key: K, value: V[K]) => setValues((v) => ({ ...v, [key]: value }));

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    const local = Object.fromEntries(
      Object.entries(validate?.(values) ?? {}).filter((entry): entry is [string, string] => !!entry[1]),
    );
    setErrors(local);
    setFormError(null);
    if (Object.keys(local).length) return;

    setSaving(true);
    try {
      const result = await save(values);
      onSaved(result);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        if (!Object.keys(err.fieldErrors).length) setFormError(err.message);
      } else {
        setFormError("Could not save. Please try again.");
      }
    } finally {
      setSaving(false);
    }
  }

  return { values, set, errors, formError, saving, submit };
}

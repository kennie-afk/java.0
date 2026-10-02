"use client";

import { useActionState, useEffect, useRef, useState } from "react";
import { emptyFormState, type FormState } from "@/lib/form-state";

export interface Field {
  name: string;
  label: string;
  type?: "text" | "number" | "tel";
  required?: boolean;
  placeholder?: string;
  step?: string;
  options?: { value: string; label: string }[];
}

const INPUT =
  "w-full rounded-lg border border-[var(--color-line-strong)] bg-[var(--color-surface)] min-h-9 px-3 py-1.5 text-base outline-none focus:border-[var(--color-accent)]";

export function EntityForm({
  action,
  title,
  submitLabel,
  fields
}: {
  action: (prev: FormState, form: FormData) => Promise<FormState>;
  title: string;
  submitLabel: string;
  fields: Field[];
}) {
  const [state, formAction, pending] = useActionState(action, emptyFormState);
  const [open, setOpen] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  useEffect(() => {
    if (state.ok) {
      formRef.current?.reset();
      setOpen(false);
    }
  }, [state.ok]);

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="rounded-lg bg-[var(--color-accent)] px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-[var(--color-good)]"
      >
        {title}
      </button>
    );
  }

  return (
    <form
      ref={formRef}
      action={formAction}
      className="mb-4 rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] shadow-[var(--shadow-card)] p-4"
    >
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold">{title}</h2>
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="text-sm text-[var(--color-muted)] hover:text-[var(--color-ink)]"
        >
          Cancel
        </button>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {fields.map((field) => (
          <label key={field.name} className="block">
            <span className="mb-1 block text-sm font-medium text-[var(--color-muted)]">
              {field.label}
              {field.required ? " *" : ""}
            </span>
            {field.options ? (
              <select name={field.name} className={INPUT} defaultValue="">
                <option value="">Select…</option>
                {field.options.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            ) : (
              <input
                name={field.name}
                type={field.type ?? "text"}
                step={field.step}
                required={field.required}
                placeholder={field.placeholder}
                className={INPUT}
              />
            )}
            {state.fieldErrors[field.name] ? (
              <span className="mt-1 block text-sm text-[var(--color-danger)]">
                {state.fieldErrors[field.name]}
              </span>
            ) : null}
          </label>
        ))}
      </div>

      {state.message ? (
        <p role="alert" className="mt-3 text-sm text-[var(--color-danger)]">
          {state.message}
        </p>
      ) : null}

      <button
        type="submit"
        disabled={pending}
        className="mt-4 rounded-lg bg-[var(--color-accent)] px-4 py-2.5 text-sm font-semibold text-white transition-opacity hover:bg-[var(--color-good)] disabled:opacity-50"
      >
        {pending ? "Saving…" : submitLabel}
      </button>
    </form>
  );
}

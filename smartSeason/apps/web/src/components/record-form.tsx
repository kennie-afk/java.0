"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { COUNTIES, subCountiesOf } from "@/lib/kenya";
import { emptyFormState, type FormState } from "@/lib/form-state";
import {
  bareInputClass,
  bareSelectClass,
  buttonClass,
  secondaryButtonClass
} from "@/components/ui";
import type { FormFieldSpec } from "@/lib/catalogue.generated";

const INPUT_TYPE: Record<string, string> = {
  int: "number",
  long: "number",
  decimal: "number",
  date: "date",
  ts: "datetime-local"
};

function step(kind: string): string | undefined {
  if (kind === "decimal") return "any";
  if (kind === "int" || kind === "long") return "1";
  return undefined;
}

export function RecordForm({
  action,
  fields,
  values,
  submitLabel,
  cancelHref,
  people = []
}: {
  action: (prev: FormState, form: FormData) => Promise<FormState>;
  fields: FormFieldSpec[];
  values?: Record<string, unknown>;
  submitLabel: string;
  cancelHref: string;
  /** People in this organisation, for fields that name one. */
  people?: { id: string; label: string }[];
}) {
  const [state, formAction, pending] = useActionState(action, emptyFormState);

  // After a failed save the action echoes back what was typed, so the user does
  // not lose their input; otherwise fall back to the stored record.
  const source: Record<string, unknown> = state.values ?? values ?? {};

  const initial = (name: string): string => {
    const value = source[name];
    if (value === null || value === undefined) return "";
    if (typeof value === "boolean") return value ? "true" : "false";
    return String(value);
  };

  // The sub-county list depends on the county chosen in this same form. The
  // override holds the user's in-page choice; until they make one, the county
  // comes from whichever source is current.
  const [countyOverride, setCountyOverride] = useState<string | null>(null);
  const county = countyOverride ?? initial("county");

  return (
    <form action={formAction} className="max-w-3xl">
      <div className="grid gap-3 rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5 sm:grid-cols-2">
        {fields.map((field) => {
          const error = state.fieldErrors[field.name];
          const label = (
            <span className="mb-1 block text-2xs font-medium uppercase tracking-[0.06em] text-[var(--color-faint)]">
              {field.label}
              {field.required ? " *" : ""}
            </span>
          );

          let control;
          if (field.lookup === "county") {
            control = (
              <select
                name={field.name}
                className={bareSelectClass}
                required={field.required}
                value={county}
                onChange={(event) => setCountyOverride(event.target.value)}
              >
                <option value="">Select…</option>
                {COUNTIES.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            );
          } else if (field.lookup === "subCounty") {
            const options = subCountiesOf(county);
            control = (
              <select
                name={field.name}
                className={bareSelectClass}
                required={field.required}
                defaultValue={initial(field.name)}
                disabled={options.length === 0}
              >
                <option value="">{county ? "Select…" : "Choose a county first"}</option>
                {options.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            );
          } else if (field.lookup === "user") {
            control = (
              <select
                name={field.name}
                className={bareSelectClass}
                required={field.required}
                defaultValue={initial(field.name)}
                disabled={people.length === 0}
              >
                <option value="">
                  {people.length === 0 ? "Nobody on the team yet" : "Select…"}
                </option>
                {people.map((person) => (
                  <option key={person.id} value={person.id}>
                    {person.label}
                  </option>
                ))}
              </select>
            );
          } else if (field.options) {
            control = (
              <select
                name={field.name}
                className={bareSelectClass}
                required={field.required}
                defaultValue={initial(field.name)}
              >
                <option value="">Select…</option>
                {field.options.map((option) => (
                  <option key={option} value={option}>
                    {option.replaceAll("_", " ")}
                  </option>
                ))}
              </select>
            );
          } else if (field.kind === "bool") {
            control = (
              <select
                name={field.name}
                className={bareSelectClass}
                defaultValue={initial(field.name) || "false"}
              >
                <option value="false">No</option>
                <option value="true">Yes</option>
              </select>
            );
          } else if (field.kind === "text" || field.kind === "json") {
            control = (
              <textarea
                name={field.name}
                rows={3}
                required={field.required}
                defaultValue={initial(field.name)}
                className={`${bareInputClass} resize-y`}
              />
            );
          } else {
            control = (
              <input
                name={field.name}
                type={INPUT_TYPE[field.kind] ?? "text"}
                step={step(field.kind)}
                required={field.required}
                defaultValue={initial(field.name)}
                className={bareInputClass}
              />
            );
          }

          const wide = field.kind === "text" || field.kind === "json";
          return (
            <label key={field.name} className={`block ${wide ? "sm:col-span-2" : ""}`}>
              {label}
              {control}
              {error ? (
                <span className="mt-1 block text-2xs text-[var(--color-danger)]">{error}</span>
              ) : null}
            </label>
          );
        })}
      </div>

      {state.message ? (
        <p role="alert" className="mt-3 text-xs text-[var(--color-danger)]">
          {state.message}
        </p>
      ) : null}

      <div className="mt-4 flex items-center gap-2">
        <button type="submit" disabled={pending} className={buttonClass}>
          {pending ? "Saving…" : submitLabel}
        </button>
        <Link href={cancelHref} className={secondaryButtonClass}>
          Cancel
        </Link>
      </div>
    </form>
  );
}

"use client";

import { useState, useTransition } from "react";
import { deleteRecord } from "@/lib/record-actions";
import { dangerButtonClass, secondaryButtonClass } from "@/components/ui";

/**
 * Delete needs a deliberate second click. It confirms inline rather than through
 * window.confirm, which a headless browser cannot dismiss and which blocks the
 * page while it is open.
 */
export function DeleteRecordButton({
  serviceSlug,
  entitySlug,
  id,
  label
}: {
  serviceSlug: string;
  entitySlug: string;
  id: string;
  label: string;
}) {
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  if (!confirming) {
    return (
      <button type="button" onClick={() => setConfirming(true)} className={dangerButtonClass}>
        Delete
      </button>
    );
  }

  return (
    <span className="inline-flex items-center gap-2">
      <span className="text-2xs text-[var(--color-muted)]">Delete this {label}?</span>
      <button
        type="button"
        disabled={pending}
        className={dangerButtonClass}
        onClick={() =>
          startTransition(async () => {
            // A successful delete redirects, so anything returned here is a failure.
            const result = await deleteRecord(serviceSlug, entitySlug, id);
            setError(result?.message ?? "The record could not be deleted");
          })
        }
      >
        {pending ? "Deleting…" : "Yes, delete"}
      </button>
      <button
        type="button"
        onClick={() => setConfirming(false)}
        className={secondaryButtonClass}
      >
        Keep
      </button>
      {error ? <span className="text-2xs text-[var(--color-danger)]">{error}</span> : null}
    </span>
  );
}

"use client";

import { useActionState, useEffect, useRef } from "react";
import { bareInputClass, bareSelectClass, buttonClass, secondaryButtonClass } from "@/components/ui";
import { claimCase, closeCase, replyToCase, resolveCase, saveCoverage } from "@/lib/case-actions";
import { idleCase, type CaseActionState } from "@/lib/case-state";

const label = "mb-1 block text-xs font-medium text-gray-600 dark:text-gray-400";

function Problem({ state }: { state: CaseActionState }) {
  return state.error ? (
    <p role="alert" className="mt-2 text-xs text-[var(--color-danger)]">
      {state.error}
    </p>
  ) : null;
}

export function ReplyForm({ id, staff }: { id: string; staff: boolean }) {
  const [state, action, pending] = useActionState(replyToCase, idleCase);
  const ref = useRef<HTMLFormElement>(null);
  useEffect(() => {
    if (state.done) ref.current?.reset();
  }, [state]);
  return (
    <form ref={ref} action={action} className="mt-3">
      <input type="hidden" name="id" value={id} />
      <label className="block">
        <span className={label}>{staff ? "Reply to the farmer" : "Add a message or another photograph description"}</span>
        <textarea name="body" rows={3} required maxLength={4000} className={`${bareInputClass} resize-y`} />
      </label>
      <Problem state={state} />
      <button type="submit" disabled={pending} className={`${buttonClass} mt-2`}>
        {pending ? "Sending…" : "Send"}
      </button>
    </form>
  );
}

export function ClaimButton({ id }: { id: string }) {
  const [state, action, pending] = useActionState(claimCase, idleCase);
  return (
    <form action={action}>
      <input type="hidden" name="id" value={id} />
      <button type="submit" disabled={pending} className={buttonClass}>
        {pending ? "Taking…" : "Take this case"}
      </button>
      <Problem state={state} />
    </form>
  );
}

export function CloseButton({ id }: { id: string }) {
  const [state, action, pending] = useActionState(closeCase, idleCase);
  return (
    <form action={action}>
      <input type="hidden" name="id" value={id} />
      <button type="submit" disabled={pending} className={secondaryButtonClass}>
        {pending ? "Closing…" : "Mark as resolved"}
      </button>
      <Problem state={state} />
    </form>
  );
}

export function ResolveForm({ id, defaultTitle }: { id: string; defaultTitle: string }) {
  const [state, action, pending] = useActionState(resolveCase, idleCase);
  return (
    <details className="panel mt-3 p-3">
      <summary className="cursor-pointer text-sm font-medium">Issue a formal advisory</summary>
      <p className="mt-2 text-xs text-[var(--color-muted)]">
        Recorded against the plot and season, visible to the farm team, and sent to the farmer.
      </p>
      <form action={action} className="mt-3 grid gap-3 sm:grid-cols-2">
        <input type="hidden" name="id" value={id} />
        <label className="block sm:col-span-2">
          <span className={label}>Title</span>
          <input name="title" required maxLength={200} defaultValue={defaultTitle} className={bareInputClass} />
        </label>
        <label className="block">
          <span className={label}>Severity</span>
          <select name="severity" defaultValue="MEDIUM" className={bareSelectClass}>
            {["INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL"].map((s) => (
              <option key={s} value={s}>
                {s.charAt(0) + s.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </label>
        <label className="block sm:col-span-2">
          <span className={label}>Advice</span>
          <textarea name="body" rows={5} required maxLength={4000} className={`${bareInputClass} resize-y`} />
        </label>
        <div className="sm:col-span-2">
          <Problem state={state} />
          <button type="submit" disabled={pending} className={`${buttonClass} mt-1`}>
            {pending ? "Issuing…" : "Issue advisory"}
          </button>
        </div>
      </form>
    </details>
  );
}

export function CoverageForm({
  counties,
  maxOpen,
  active
}: {
  counties: string[];
  maxOpen: number;
  active: boolean;
}) {
  const [state, action, pending] = useActionState(saveCoverage, idleCase);
  return (
    <form action={action} className="space-y-3">
      <label className="block">
        <span className={label}>Counties I cover</span>
        <input
          name="counties"
          defaultValue={counties.join(", ")}
          placeholder="Nakuru, Kiambu (blank covers every county)"
          className={bareInputClass}
        />
      </label>
      <label className="block">
        <span className={label}>Most cases I take at once</span>
        <input name="maxOpen" type="number" min={1} max={500} defaultValue={maxOpen} className={bareInputClass} />
      </label>
      <label className="flex items-center gap-2 text-xs">
        <input type="checkbox" name="active" defaultChecked={active} />
        Receive new cases (untick while away)
      </label>
      <Problem state={state} />
      {state.done ? <p className="text-xs text-[var(--color-good)]">Saved.</p> : null}
      <button type="submit" disabled={pending} className={buttonClass}>
        {pending ? "Saving…" : "Save"}
      </button>
    </form>
  );
}

"use client";

import { startTransition, useActionState, useState } from "react";
import { bareInputClass, buttonClass, secondaryButtonClass } from "@/components/ui";
import { raiseCase } from "@/lib/case-actions";
import { idleCase } from "@/lib/case-state";
import type { Diagnosis } from "@/lib/advisor-types";

/** Shrinks a photograph in the browser so it is quick to send and small to store. */
async function shrink(file: File): Promise<File> {
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext("2d")?.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.82));
    if (blob) return new File([blob], file.name.replace(/\.\w+$/, "") + ".jpg", { type: "image/jpeg" });
  } catch {
    /* fall through to the original */
  }
  return file;
}

export interface Asked {
  crop: string;
  county: string;
  symptoms: string;
  files: File[];
}

/**
 * Sends the case to a person. The AI answer stays attached so the agronomist starts from it
 * rather than from nothing, and a low-confidence or serious diagnosis is flagged as such.
 */
export function AskAgronomist({ diagnosis, asked }: { diagnosis: Diagnosis | null; asked: Asked }) {
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState(asked.symptoms);
  const [preparing, setPreparing] = useState(false);
  const [state, action, pending] = useActionState(raiseCase, idleCase);

  // With no diagnosis (the AI could not answer) the case goes to a person on its own.
  const top = diagnosis?.candidates[0];
  const unsure = diagnosis !== null && (!top || top.confidence < 0.6);
  const serious = diagnosis?.severity === "HIGH" || diagnosis?.severity === "CRITICAL";
  const reason = serious ? "HIGH_SEVERITY" : unsure ? "LOW_CONFIDENCE" : "REQUESTED";

  async function send() {
    setPreparing(true);
    const form = new FormData();
    form.set("cropCode", asked.crop);
    form.set("county", asked.county);
    form.set("question", note);
    form.set("reason", reason);
    if (diagnosis) {
      form.set("aiSource", diagnosis.source);
      form.set("aiSeverity", diagnosis.severity);
    }
    if (diagnosis && top) {
      form.set("aiSummary", `${top.commonName ?? "Unknown"} (${Math.round(top.confidence * 100)}% sure). ${diagnosis.management}`.slice(0, 3900));
      form.set("aiConfidence", String(top.confidence));
    }
    for (const file of await Promise.all(asked.files.slice(0, 3).map(shrink))) form.append("photos", file);
    setPreparing(false);
    startTransition(() => action(form));
  }

  return (
    <div className="mt-4 border-t border-[var(--color-line)] pt-3">
      {unsure || serious ? (
        <p className="mb-2 text-xs text-[var(--color-warn)]">
          {serious
            ? "This looks serious. An agronomist should confirm it."
            : "The model is not confident. An agronomist can look at the photographs."}
        </p>
      ) : null}
      {!open ? (
        <button type="button" onClick={() => setOpen(true)} className={unsure || serious ? buttonClass : secondaryButtonClass}>
          Ask an agronomist
        </button>
      ) : (
        <div>
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-400">
              What should the agronomist know?
            </span>
            <textarea
              value={note}
              onChange={(event) => setNote(event.target.value)}
              rows={3}
              maxLength={4000}
              className={`${bareInputClass} resize-y`}
            />
          </label>
          <p className="mt-1 text-xs text-[var(--color-muted)]">
            Sent{(() => {
              const parts = [
                diagnosis ? "the diagnosis above" : "",
                asked.files.length > 0 ? `${Math.min(3, asked.files.length)} photograph${asked.files.length > 1 ? "s" : ""}` : ""
              ].filter(Boolean);
              return parts.length ? ` with ${parts.join(" and ")}` : "";
            })()}
            {asked.county ? `, to an agronomist covering ${asked.county}` : ""}.
          </p>
          {state.error ? (
            <p role="alert" className="mt-2 text-xs text-[var(--color-danger)]">
              {state.error}
            </p>
          ) : null}
          <div className="mt-2 flex gap-2">
            <button type="button" onClick={send} disabled={pending || preparing} className={buttonClass}>
              {preparing || pending ? "Sending…" : "Send to an agronomist"}
            </button>
            <button type="button" onClick={() => setOpen(false)} className={secondaryButtonClass}>
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

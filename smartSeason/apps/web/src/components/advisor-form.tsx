"use client";

import { useActionState, useState } from "react";
import { Badge, bareInputClass, bareSelectClass, buttonClass } from "@/components/ui";
import { diagnose } from "@/lib/advisor";
import { emptyDiagnosis } from "@/lib/advisor-types";

const CROPS = ["MAIZE", "POTATO", "BEANS", "TOMATO", "AVOCADO"];

export function AdvisorForm() {
  const [state, action, pending] = useActionState(diagnose, emptyDiagnosis);
  const [previews, setPreviews] = useState<string[]>([]);

  const label = "mb-1 block text-2xs font-medium uppercase tracking-[0.06em] text-[var(--color-faint)]";

  return (
    <div className="grid gap-2.5 lg:grid-cols-2">
      <form action={action} className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className={label}>Crop</span>
            <select name="cropCode" defaultValue={state.crop} className={bareSelectClass}>
              {CROPS.map((crop) => (
                <option key={crop} value={crop}>
                  {crop.charAt(0) + crop.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className={label}>Growth stage</span>
            <input name="growthStage" placeholder="Tasseling" className={bareInputClass} />
          </label>
          <label className="block sm:col-span-2">
            <span className={label}>County</span>
            <input name="county" placeholder="Nakuru" className={bareInputClass} />
          </label>
          <label className="block sm:col-span-2">
            <span className={label}>What can you see?</span>
            <textarea
              name="symptoms"
              rows={4}
              defaultValue={state.symptoms}
              placeholder="Ragged holes in the whorl, frass, window-paning on young leaves…"
              className={`${bareInputClass} resize-y`}
            />
          </label>
          <label className="block sm:col-span-2">
            <span className={label}>Photographs</span>
            <input
              type="file"
              name="photos"
              accept="image/*"
              multiple
              onChange={(event) => {
                const files = Array.from(event.target.files ?? []);
                setPreviews(files.map((file) => URL.createObjectURL(file)));
              }}
              className="block w-full text-2xs file:mr-3 file:cursor-pointer file:rounded-md file:border file:border-[var(--color-line)] file:bg-[var(--color-raised)] file:px-2.5 file:py-1.5 file:text-2xs file:font-medium"
            />
            <span className="mt-1 block text-2xs text-[var(--color-muted)]">
              Up to four photographs, 5MB each. Get close to the affected leaf and keep it in focus.
            </span>
          </label>
        </div>

        {previews.length > 0 ? (
          <div className="mt-3 flex flex-wrap gap-2">
            {previews.map((src) => (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                key={src}
                src={src}
                alt=""
                className="h-16 w-16 rounded-md border border-[var(--color-line)] object-cover"
              />
            ))}
          </div>
        ) : null}

        {state.error ? (
          <p role="alert" className="mt-3 text-2xs text-[var(--color-danger)]">
            {state.error}
          </p>
        ) : null}

        <button type="submit" disabled={pending} className={`${buttonClass} mt-4`}>
          {pending ? "Examining…" : "Diagnose"}
        </button>
      </form>

      <section className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5">
        {!state.result ? (
          <p className="text-2xs text-[var(--color-muted)]">
            Describe the problem or attach a photograph, and the diagnosis appears here.
          </p>
        ) : (
          <>
            <div className="mb-2 flex items-center justify-between gap-3">
              <h2 className="text-xs font-semibold">Diagnosis</h2>
              <span className="flex items-center gap-2">
                <Badge value={state.result.severity} />
                <span className="text-2xs text-[var(--color-faint)]">
                  {state.result.source === "MODEL" ? "vision model" : "built-in rules"}
                </span>
              </span>
            </div>

            {state.result.candidates.length === 0 ? (
              <p className="text-2xs text-[var(--color-muted)]">
                Nothing matched confidently.
              </p>
            ) : (
              <ul className="space-y-2">
                {state.result.candidates.map((candidate, index) => (
                  <li
                    key={`${candidate.code}-${index}`}
                    className="rounded-md border border-[var(--color-line)] px-2.5 py-2"
                  >
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="text-xs font-medium">{candidate.commonName}</span>
                      <span className="tabular-nums text-2xs text-[var(--color-muted)]">
                        {Math.round(candidate.confidence * 100)}%
                      </span>
                    </div>
                    {candidate.scientificName ? (
                      <p className="text-2xs italic text-[var(--color-faint)]">
                        {candidate.scientificName}
                      </p>
                    ) : null}
                    {candidate.reasoning ? (
                      <p className="mt-1 text-2xs leading-relaxed text-[var(--color-muted)]">
                        {candidate.reasoning}
                      </p>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}

            {state.result.management ? (
              <>
                <h3 className="mt-3 text-2xs font-medium uppercase tracking-[0.06em] text-[var(--color-faint)]">
                  What to do
                </h3>
                <p className="mt-1 text-xs leading-relaxed">{state.result.management}</p>
              </>
            ) : null}

            {state.result.caveats.length > 0 ? (
              <ul className="mt-3 space-y-0.5">
                {state.result.caveats.map((caveat) => (
                  <li key={caveat} className="text-2xs text-[var(--color-warn)]">
                    {caveat}
                  </li>
                ))}
              </ul>
            ) : null}
          </>
        )}
      </section>
    </div>
  );
}

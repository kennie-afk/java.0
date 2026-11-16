"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { addDays, CROPS, totalDays } from "@/lib/crop-calendar";
import { emptyFormState } from "@/lib/form-state";
import { startSeason } from "@/lib/season-actions";
import { bareInputClass, bareSelectClass, buttonClass, secondaryButtonClass } from "@/components/ui";

export interface PlotOption {
  id: string;
  label: string;
}

const today = () => new Date().toISOString().slice(0, 10);

export function StartSeasonForm({ plots }: { plots: PlotOption[] }) {
  const [state, action, pending] = useActionState(startSeason, emptyFormState);
  const [cropCode, setCropCode] = useState(state.values?.cropCode ?? "");
  const [start, setStart] = useState(state.values?.startDate ?? today());
  const crop = CROPS.find((c) => c.code === cropCode);
  const err = (name: string) =>
    state.fieldErrors[name] ? (
      <span className="mt-1 block text-xs text-[var(--color-danger)]">{state.fieldErrors[name]}</span>
    ) : null;
  const label = "mb-1 block text-xs font-medium text-gray-600 dark:text-gray-400";

  let cursor = start;
  return (
    <form action={action} className="max-w-3xl">
      <div className="panel grid gap-5 p-3 sm:grid-cols-2">
        <label className="block">
          <span className={label}>Plot *</span>
          <select name="plotId" required defaultValue={state.values?.plotId ?? ""} className={bareSelectClass}>
            <option value="">{plots.length ? "Select…" : "No plots yet: add one under Farm"}</option>
            {plots.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
          </select>
          {err("plotId")}
        </label>
        <label className="block">
          <span className={label}>Crop *</span>
          <select name="cropCode" required value={cropCode} onChange={(e) => setCropCode(e.target.value)} className={bareSelectClass}>
            <option value="">Select…</option>
            {CROPS.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
          </select>
          {err("cropCode")}
        </label>
        <label className="block">
          <span className={label}>Planting date *</span>
          <input name="startDate" type="date" required value={start} onChange={(e) => setStart(e.target.value)} className={bareInputClass} />
          {err("startDate")}
        </label>
        <label className="block">
          <span className={label}>Variety</span>
          <input name="variety" defaultValue={state.values?.variety ?? ""} placeholder="e.g. H614D, Shangi" className={bareInputClass} />
        </label>
        <label className="block sm:col-span-2">
          <span className={label}>Expected yield, kg</span>
          <input name="expectedYieldKg" type="number" step="any" defaultValue={state.values?.expectedYieldKg ?? ""}
            placeholder="Leave blank to use the typical yield for this crop and plot size" className={bareInputClass} />
          {err("expectedYieldKg")}
        </label>
      </div>

      {crop ? (
        <div className="panel mt-4 p-3">
          <p className="text-xs font-medium text-gray-600 dark:text-gray-400">
            Calendar that will be created · harvest about {addDays(start, totalDays(crop))} ({totalDays(crop)} days)
          </p>
          <ol className="mt-2 space-y-1 text-sm">
            {crop.stages.map((stage) => {
              const from = cursor;
              cursor = addDays(cursor, stage.days);
              return (
                <li key={stage.name} className="flex justify-between gap-4">
                  <span>{stage.name}</span>
                  <span className="text-muted">{from} to {cursor} · {stage.days} days</span>
                </li>
              );
            })}
          </ol>
          <p className="mt-2 text-xs text-muted">Status, stages and the harvest date are set for you. Actual harvest date and yield are recorded when you harvest.</p>
        </div>
      ) : null}

      {state.message ? <p role="alert" className="mt-3 text-sm text-[var(--color-danger)]">{state.message}</p> : null}

      <div className="mt-4 flex items-center gap-2">
        <button type="submit" disabled={pending} className={buttonClass}>{pending ? "Starting…" : "Start season"}</button>
        <Link href="/season/seasons" className={secondaryButtonClass}>Cancel</Link>
      </div>
    </form>
  );
}

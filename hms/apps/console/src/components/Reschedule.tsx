"use client";

import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { addDays, time, today } from "@/lib/format";
import { rescheduleBody } from "@/lib/rules";
import { Button, ErrorNote, Input, useAction } from "./ui";

type Slot = { start: string; end: string; practitionerId: string };

/** Moves a booked appointment to another free slot with the same practitioner. The version stops two clerks overwriting each other. */
export function Reschedule({ id, clinicId, practitionerId, version, onDone }: { id: string; clinicId: string; practitionerId?: string; version: number; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [day, setDay] = useState(addDays(today(), 1));
  const [slot, setSlot] = useState<Slot | null>(null);
  const slots = useFetch<Slot[]>(open ? `/v1/scheduling/slots?clinicId=${clinicId}&date=${day}${practitionerId ? `&practitionerId=${practitionerId}` : ""}` : null);
  const { busy, error, run } = useAction();
  if (!open) return <Button variant="secondary" onClick={() => setOpen(true)}>Reschedule</Button>;
  return (
    <div className="space-y-2 rounded-lg border border-line bg-surface p-3">
      <Input type="date" min={today()} value={day} onChange={(e) => { setDay(e.target.value); setSlot(null); }} className="max-w-40" aria-label="New day" />
      <div className="flex flex-wrap gap-1">
        {(slots.data ?? []).map((s) => (
          <button key={s.start} type="button" onClick={() => setSlot(s)} className={`cursor-pointer rounded-lg border px-2.5 py-1 text-sm font-semibold ${slot?.start === s.start ? "border-accent bg-accent-soft text-accent" : "border-line"}`}>{time(s.start)}</button>
        ))}
        {slots.data?.length === 0 && <span className="text-sm text-muted">No free slots that day.</span>}
      </div>
      <ErrorNote error={error} />
      <div className="flex gap-2">
        <Button busy={busy} disabled={!slot} onClick={() => void run(async () => { await post(`/v1/scheduling/appointments/${id}/reschedule`, rescheduleBody(slot!.start, version)); setOpen(false); onDone(); })}>Move</Button>
        <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
      </div>
    </div>
  );
}

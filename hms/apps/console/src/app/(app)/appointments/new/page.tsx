"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { addDays, time, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

type Clinic = { id: string; name: string; active: boolean; slotMinutes: number };
type Slot = { start: string; end: string; practitionerId: string };

export default function BookAppointment() {
  const { facilityId } = useSession();
  const router = useRouter();
  const clinics = useFetch<Clinic[]>(`/v1/scheduling/clinics?facilityId=${facilityId}`);
  const [clinicId, setClinicId] = useState("");
  const [day, setDay] = useState(addDays(today(), 1));
  const [patient, setPatient] = useState<PatientRow | null>(null);
  const [slot, setSlot] = useState<Slot | null>(null);
  const [reason, setReason] = useState("");
  const active = (clinics.data ?? []).filter((c) => c.active);
  const chosen = clinicId || active[0]?.id || "";
  const slots = useFetch<Slot[]>(chosen ? `/v1/scheduling/slots?clinicId=${chosen}&date=${day}` : null);
  const { busy, error, run } = useAction();
  return (
    <Page title="Book appointment">
      <Card>
        <Grid cols={3}>
          <Field label="Clinic"><Select value={chosen} onChange={(e) => { setClinicId(e.target.value); setSlot(null); }}>{active.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select></Field>
          <Field label="Day"><Input type="date" min={today()} value={day} onChange={(e) => { setDay(e.target.value); setSlot(null); }} /></Field>
          <Field label="Patient"><PatientPicker value={patient} onPick={setPatient} /></Field>
        </Grid>
      </Card>
      <Card title="Free slots">
        <div className="flex flex-wrap gap-1">
          {(slots.data ?? []).map((s) => (
            <button key={s.start} type="button" onClick={() => setSlot(s)} className={`cursor-pointer rounded-lg border px-3 py-2 text-sm font-semibold ${slot?.start === s.start ? "border-accent bg-accent-soft font-semibold text-accent" : "border-line hover:bg-raised"}`}>{time(s.start)}</button>
          ))}
          {slots.data?.length === 0 && <span className="text-sm text-muted">No free slots that day.</span>}
        </div>
      </Card>
      <Field label="Reason"><Input value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
      <ErrorNote error={error} />
      <Button busy={busy} disabled={!patient || !slot} onClick={() => void run(async () => {
        await post("/v1/scheduling/appointments", { clinicId: chosen, patientId: patient!.id, practitionerId: slot!.practitionerId, startsAt: slot!.start, reason: reason || undefined });
        router.push("/appointments");
      })}>Book</Button>
    </Page>
  );
}

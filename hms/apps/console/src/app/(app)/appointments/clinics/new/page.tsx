"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { post, useFetch, type Slice } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

type Staff = { id: string; fullName: string; cadre: string };
const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export default function NewClinic() {
  const { facilityId } = useSession();
  const router = useRouter();
  const staff = useFetch<Slice<Staff>>("/v1/staff?status=ACTIVE&limit=100");
  const [f, setF] = useState({ name: "", specialty: "", slot: "15", practitionerId: "", start: "08:00", end: "17:00" });
  const [days, setDays] = useState([1, 2, 3, 4, 5]);
  const { busy, error, run } = useAction();
  const who = f.practitionerId || staff.data?.data[0]?.id || "";
  return (
    <Page title="New clinic" sub="Opening hours for one practitioner. Add more practitioners by editing the clinic through the API for now.">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => {
        await post("/v1/scheduling/clinics", { facilityId, name: f.name, specialty: f.specialty || undefined, slotMinutes: Number(f.slot), sessions: days.map((d) => ({ practitionerId: who, weekday: d, startTime: `${f.start}:00`, endTime: `${f.end}:00` })) });
        router.push("/appointments");
      }); }}>
        <Card><Grid cols={3}>
          <Field label="Name"><Input required value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
          <Field label="Specialty"><Input value={f.specialty} onChange={(e) => setF({ ...f, specialty: e.target.value })} /></Field>
          <Field label="Slot length (minutes)"><Input type="number" min={5} max={120} value={f.slot} onChange={(e) => setF({ ...f, slot: e.target.value })} /></Field>
          <Field label="Practitioner"><Select value={who} onChange={(e) => setF({ ...f, practitionerId: e.target.value })}>{(staff.data?.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.fullName} ({s.cadre})</option>)}</Select></Field>
          <Field label="From"><Input type="time" value={f.start} onChange={(e) => setF({ ...f, start: e.target.value })} /></Field>
          <Field label="To"><Input type="time" value={f.end} onChange={(e) => setF({ ...f, end: e.target.value })} /></Field>
        </Grid>
        <div className="flex gap-3 pt-3">{DAYS.map((d, i) => <label key={d} className="flex items-center gap-1 text-sm"><input type="checkbox" checked={days.includes(i + 1)} onChange={(e) => setDays(e.target.checked ? [...days, i + 1] : days.filter((x) => x !== i + 1))} /> {d}</label>)}</div></Card>
        <ErrorNote error={error} /><Button type="submit" busy={busy} disabled={!who || days.length === 0}>Create clinic</Button>
      </form>
    </Page>
  );
}

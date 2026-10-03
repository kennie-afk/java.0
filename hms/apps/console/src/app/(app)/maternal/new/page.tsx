"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError, post } from "@/lib/api";
import { today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, useAction } from "@/components/ui";

export default function NewPregnancy() {
  const { facilityId } = useSession();
  const router = useRouter();
  const [patient, setPatient] = useState<PatientRow | null>(null);
  const [f, setF] = useState({ lmp: "", gravida: "1", parity: "0" });
  const { busy, error, run } = useAction();
  void ApiError;
  return (
    <Page title="Open pregnancy">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { const p = await post<{ id: string }>("/v1/mch/pregnancies", { facilityId, patientId: patient!.id, lmp: f.lmp, gravida: Number(f.gravida), parity: Number(f.parity) }); router.push(`/maternal/${p.id}`); }); }}>
        <Card><Grid cols={2}>
          <Field label="Mother"><PatientPicker value={patient} onPick={setPatient} /></Field>
          <Field label="Last menstrual period" hint="The due date and gestation are calculated from this"><Input type="date" required max={today()} value={f.lmp} onChange={(e) => setF({ ...f, lmp: e.target.value })} /></Field>
          <Field label="Gravida" hint="Pregnancies including this one"><Input type="number" min={1} max={20} required value={f.gravida} onChange={(e) => setF({ ...f, gravida: e.target.value })} /></Field>
          <Field label="Parity" hint="Births at or beyond viability, before this one"><Input type="number" min={0} max={19} required value={f.parity} onChange={(e) => setF({ ...f, parity: e.target.value })} /></Field>
        </Grid></Card>
        <ErrorNote error={error} /><Button type="submit" busy={busy} disabled={!patient}>Open pregnancy</Button>
      </form>
    </Page>
  );
}

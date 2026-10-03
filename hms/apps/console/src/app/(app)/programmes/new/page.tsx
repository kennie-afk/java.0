"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api, post } from "@/lib/api";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

export default function Enrol() {
  const { facilityId, can } = useSession();
  const sp = useSearchParams();
  const router = useRouter();
  const [patient, setPatient] = useState<PatientRow | null>(null);
  const [f, setF] = useState({ programme: "HYPERTENSION", enrolledOn: "", regimen: "", nextVisitOn: "" });
  const { busy, error, run } = useAction();
  const pid = sp.get("patientId");
  useEffect(() => {
    if (pid) api<PatientRow>(`/v1/patients/${pid}`).then(setPatient).catch(() => undefined);
  }, [pid]);
  const programmes = ["TB", "HYPERTENSION", "DIABETES", "ASTHMA", "EPILEPSY", ...(can("programmes:hiv") ? ["HIV"] : [])];
  return (
    <Page title="Enrol in a programme">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { const r = await post<{ id: string }>("/v1/programmes/enrolments", { facilityId, patientId: patient!.id, programme: f.programme, enrolledOn: f.enrolledOn || undefined, regimen: f.regimen || undefined, nextVisitOn: f.nextVisitOn || undefined }); router.push(`/programmes/${r.id}`); }); }}>
        <Card><Grid cols={2}>
          <Field label="Patient"><PatientPicker value={patient} onPick={setPatient} /></Field>
          <Field label="Programme"><Select value={f.programme} onChange={(e) => setF({ ...f, programme: e.target.value })}>{programmes.map((p) => <option key={p}>{p}</option>)}</Select></Field>
          <Field label="Enrolled on" hint="Leave blank for today"><Input type="date" value={f.enrolledOn} onChange={(e) => setF({ ...f, enrolledOn: e.target.value })} /></Field>
          <Field label="Next visit"><Input type="date" value={f.nextVisitOn} onChange={(e) => setF({ ...f, nextVisitOn: e.target.value })} /></Field>
          <Field label="Regimen or treatment"><Input value={f.regimen} onChange={(e) => setF({ ...f, regimen: e.target.value })} /></Field>
        </Grid></Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy} disabled={!patient}>Enrol</Button>
      </form>
    </Page>
  );
}

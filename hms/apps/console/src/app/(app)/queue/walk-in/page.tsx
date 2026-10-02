"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

type Clinic = { id: string; name: string; active: boolean };

export default function WalkIn() {
  const { facilityId } = useSession();
  const router = useRouter();
  const clinics = useFetch<Clinic[]>(`/v1/scheduling/clinics?facilityId=${facilityId}`);
  const [patient, setPatient] = useState<PatientRow | null>(null);
  const [clinicId, setClinicId] = useState("");
  const [priority, setPriority] = useState("ROUTINE");
  const [reason, setReason] = useState("");
  const { busy, error, run } = useAction();
  const active = (clinics.data ?? []).filter((c) => c.active);
  return (
    <Page title="Add walk-in">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { await post("/v1/scheduling/walk-ins", { clinicId: clinicId || active[0]?.id, patientId: patient?.id, priority, reason: reason || undefined }); router.push("/queue"); }); }}>
        <Card>
          <Grid cols={2}>
            <Field label="Patient"><PatientPicker value={patient} onPick={setPatient} /></Field>
            <Field label="Clinic"><Select value={clinicId || active[0]?.id || ""} onChange={(e) => setClinicId(e.target.value)}>{active.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select></Field>
            <Field label="Priority"><Select value={priority} onChange={(e) => setPriority(e.target.value)}><option>ROUTINE</option><option>PRIORITY</option><option>EMERGENCY</option></Select></Field>
            <Field label="Reason"><Input value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
          </Grid>
          {!clinics.loading && active.length === 0 && <p className="pt-2 text-xs text-warn">This facility has no clinic yet. An administrator can create one: Appointments, then New clinic.</p>}
        </Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy} disabled={!patient || active.length === 0}>Add to queue</Button>
      </form>
    </Page>
  );
}

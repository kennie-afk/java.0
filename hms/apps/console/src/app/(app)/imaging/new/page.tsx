"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api, post, useFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

type Procedure = { id: string; code: string; name: string; modality: string };

export default function NewImagingOrder() {
  const { facilityId } = useSession();
  const sp = useSearchParams();
  const router = useRouter();
  const procedures = useFetch<Procedure[]>("/v1/imaging/procedures");
  const [patient, setPatient] = useState<PatientRow | null>(null);
  const [procedureId, setProcedureId] = useState("");
  const [priority, setPriority] = useState("ROUTINE");
  const [info, setInfo] = useState("");
  const { busy, error, run } = useAction();
  const pid = sp.get("patientId");
  useEffect(() => {
    if (pid) api<PatientRow>(`/v1/patients/${pid}`).then(setPatient).catch(() => undefined);
  }, [pid]);
  return (
    <Page title="New imaging order">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { const o = await post<{ id: string }>("/v1/imaging/orders", { facilityId, patientId: patient!.id, encounterId: sp.get("encounterId") ?? undefined, procedureId, priority, clinicalInfo: info || undefined }); router.push(`/imaging/${o.id}`); }); }}>
        <Card><Grid cols={2}>
          <Field label="Patient"><PatientPicker value={patient} onPick={setPatient} /></Field>
          <Field label="Procedure"><Select required value={procedureId} onChange={(e) => setProcedureId(e.target.value)}><option value="">Choose</option>{(procedures.data ?? []).map((p) => <option key={p.id} value={p.id}>{p.name} ({p.modality})</option>)}</Select></Field>
          <Field label="Priority"><Select value={priority} onChange={(e) => setPriority(e.target.value)}><option>ROUTINE</option><option>URGENT</option><option>STAT</option></Select></Field>
          <Field label="Clinical information"><Input value={info} onChange={(e) => setInfo(e.target.value)} /></Field>
        </Grid></Card>
        {procedures.data?.length === 0 && <p className="text-sm text-muted">The catalogue is empty. Add procedures under Procedures.</p>}
        <ErrorNote error={error} />
        <Button type="submit" busy={busy} disabled={!patient || !procedureId}>Place order</Button>
      </form>
    </Page>
  );
}

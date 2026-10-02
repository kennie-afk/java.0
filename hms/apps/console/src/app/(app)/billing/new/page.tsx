"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api, post, usePaged } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

type Enc = { id: string; type: string; startedAt: string; status: string };

export default function NewInvoice() {
  const { facilityId } = useSession();
  const router = useRouter();
  const sp = useSearchParams();
  const [patient, setPatient] = useState<PatientRow | null>(null);
  const [encounterId, setEncounterId] = useState("");
  const [payerType, setPayerType] = useState("CASH");
  const [payerName, setPayerName] = useState("");
  const encs = usePaged<Enc>(patient ? `/v1/clinical/encounters?patientId=${patient.id}` : null);
  const pid = sp.get("patientId");
  useEffect(() => { if (pid) api<PatientRow>(`/v1/patients/${pid}`).then(setPatient).catch(() => undefined); }, [pid]);
  const { busy, error, run } = useAction();
  return (
    <Page title="New invoice">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { const i = await post<{ id: string }>("/v1/billing/invoices", { facilityId, patientId: patient!.id, encounterId: encounterId || undefined, payerType, payerName: payerName || undefined }); router.push(`/billing/${i.id}`); }); }}>
        <Card><Grid cols={2}>
          <Field label="Patient"><PatientPicker value={patient} onPick={setPatient} /></Field>
          <Field label="Visit" hint="Link a visit to bill what was dispensed and tested, and to claim from it"><Select value={encounterId} onChange={(e) => setEncounterId(e.target.value)}><option value="">None</option>{encs.items.map((x) => <option key={x.id} value={x.id}>{x.type} {stamp(x.startedAt)} {x.status}</option>)}</Select></Field>
          <Field label="Payer"><Select value={payerType} onChange={(e) => setPayerType(e.target.value)}><option>CASH</option><option>SHA</option><option>INSURER</option></Select></Field>
          <Field label="Payer name"><Input value={payerName} onChange={(e) => setPayerName(e.target.value)} /></Field>
        </Grid></Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy} disabled={!patient}>Create draft</Button>
      </form>
    </Page>
  );
}

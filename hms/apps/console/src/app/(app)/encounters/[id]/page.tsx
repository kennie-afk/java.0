"use client";

import { use } from "react";
import { post, useFetch } from "@/lib/api";
import { age, date, stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, Confirm, ErrorNote, Grid, KV, Loading, Page, Status, Table, Td, Tr, Notice } from "@/components/ui";

type Vitals = { id: string; recordedAt: string; tempC?: number; pulse?: number; respRate?: number; systolic?: number; diastolic?: number; spo2?: number; weightKg?: number; bmi?: number; retracted: boolean; alerts: string[] };
type Note = { id: string; threadId: string; version: number; kind: string; body: string; createdAt: string; amendReason?: string };
type Dx = { id: string; icd11Code: string; title: string; kind: string; certainty: string };
type Order = { id: string; kind: string; status: string; description: string; drugName?: string; dose?: string; frequency?: string; quantity?: number; dispensedQuantity?: number; allergyOverrideReason?: string };
type Allergy = { id: string; substance: string; severity: string; status: string };
type Detail = {
  encounter: { id: string; patientId: string; type: string; status: string; chiefComplaint?: string; triageCategory?: string; startedAt: string };
  vitals: Vitals[]; notes: Note[]; diagnoses: Dx[]; orders: Order[]; allergies: Allergy[];
};
type Patient = { id: string; givenName: string; familyName: string; sex: string; birthDate: string };

export default function Chart({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const d = useFetch<Detail>(`/v1/clinical/encounters/${id}`);
  const p = useFetch<Patient>(d.data ? `/v1/patients/${d.data.encounter.patientId}` : null);
  if (d.loading || !d.data) return <Page title="Visit">{d.error ? <ErrorNote error={d.error} /> : <Loading />}</Page>;
  const e = d.data.encounter;
  const open = e.status === "OPEN";
  const rec = (kind: string) => `/encounters/${id}/record?kind=${kind}`;
  const write = can("clinical:write") && open;
  const activeAllergies = d.data.allergies.filter((a) => a.status === "ACTIVE");

  return (
    <Page title={p.data ? `${p.data.givenName} ${p.data.familyName}` : "Visit"} sub={p.data ? `${p.data.sex} · ${age(p.data.birthDate)} · born ${date(p.data.birthDate)} · ${e.type} visit started ${stamp(e.startedAt)}` : undefined}
      actions={<>
        <Status value={e.status} />
        {e.triageCategory && <Status value={e.triageCategory} />}
        {write && <Button variant="secondary" href={rec("triage")}>Triage</Button>}
        {can("orders:write") && open && <Button variant="secondary" href={`/lab/new?patientId=${e.patientId}&encounterId=${id}`}>Order lab tests</Button>}
        {write && <Confirm label="Close visit" variant="secondary" prompt="Close this visit? It needs a primary diagnosis, or a reason why there is none." needsReason minReason={0}
          onConfirm={async (reason) => { await post(`/v1/clinical/encounters/${id}/close`, reason ? { noDiagnosisReason: reason } : {}); await d.reload(); }} />}
      </>}>
      {activeAllergies.length > 0 && (
        <Notice tone="danger" title="Allergies">{activeAllergies.map((a) => `${a.substance} (${a.severity})`).join(", ")}</Notice>
      )}
      {e.chiefComplaint && <div className="text-sm"><b>Complaint:</b> {e.chiefComplaint}</div>}
      <Grid cols={2}>
        <Card title="Vitals" actions={write && <Button variant="secondary" href={rec("vitals")}>Record</Button>} pad={false}>
          <Table head={["When", "Temp", "Pulse", "BP", "SpO2", "RR", "Wt", "Alerts"]} empty="No readings.">
            {d.data.vitals.map((v) => (
              <Tr key={v.id}>
                <Td className={v.retracted ? "line-through text-faint" : ""}>{stamp(v.recordedAt)}</Td>
                <Td>{v.tempC}</Td><Td>{v.pulse}</Td><Td>{v.systolic ? `${v.systolic}/${v.diastolic}` : ""}</Td><Td>{v.spo2}</Td><Td>{v.respRate}</Td><Td>{v.weightKg}</Td>
                <Td>{v.retracted ? <Badge>Error</Badge> : v.alerts.map((a) => <Badge key={a} tone="danger">{a.replaceAll("_", " ")}</Badge>)}</Td>
              </Tr>
            ))}
          </Table>
        </Card>
        <Card title="Diagnoses (ICD-11)" actions={write && <Button variant="secondary" href={rec("diagnosis")}>Add</Button>} pad={false}>
          <Table head={["Code", "Diagnosis", "Kind", "Certainty"]} empty="None yet.">
            {d.data.diagnoses.map((x) => <Tr key={x.id}><Td>{x.icd11Code}</Td><Td>{x.title}</Td><Td>{x.kind}</Td><Td>{x.certainty}</Td></Tr>)}
          </Table>
        </Card>
      </Grid>
      <Card title="Notes" actions={write && <Button variant="secondary" href={rec("note")}>Write note</Button>}>
        {d.data.notes.length === 0 ? <div className="text-sm text-muted">No notes.</div> : (
          <ul className="space-y-3">
            {d.data.notes.map((n) => (
              <li key={n.id} className="text-sm">
                <div className="flex items-center gap-2 text-muted"><Badge>{n.kind}</Badge> {stamp(n.createdAt)} {n.version > 1 && <Badge tone="warn">Amended v{n.version}</Badge>}
                  {can("clinical:write") && <a className="text-accent hover:underline" href={`/encounters/${id}/record?kind=amend&thread=${n.threadId}`}>Amend</a>}</div>
                <p className="whitespace-pre-wrap pt-1">{n.body}</p>
                {n.amendReason && <p className="pt-1 text-muted">Reason for amendment: {n.amendReason}</p>}
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card title="Orders and prescriptions" actions={can("orders:write") && open && <Button variant="secondary" href={rec("order")}>New order</Button>} pad={false}>
        <Table head={["Order", "Detail", "Status", ""]} empty="No orders.">
          {d.data.orders.map((o) => (
            <Tr key={o.id}>
              <Td>{o.kind}</Td>
              <Td>{o.kind === "MEDICATION" ? `${o.drugName} ${o.dose ?? ""} ${o.frequency ?? ""} x${o.quantity} (dispensed ${o.dispensedQuantity})` : o.description}
                {o.allergyOverrideReason && <Badge tone="warn">Allergy override</Badge>}</Td>
              <Td><Status value={o.status} /></Td>
              <Td>{o.status === "ORDERED" && can("orders:write") && <Confirm label="Cancel" prompt="Cancel this order?" needsReason minReason={3} onConfirm={async (reason) => { await post(`/v1/clinical/orders/${o.id}/cancel`, { reason }); await d.reload(); }} />}</Td>
            </Tr>
          ))}
        </Table>
      </Card>
      <Card title="Reference"><Grid cols={3}><KV k="Encounter" v={e.id} /><KV k="Patient" v={<a className="text-accent hover:underline" href={`/patients/${e.patientId}`}>Open record</a>} /></Grid></Card>
    </Page>
  );
}

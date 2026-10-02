"use client";

import { use, useState } from "react";
import { post, useFetch } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Field, Grid, Input, KV, Loading, Page, Select, Status, Table, Td, Textarea, Tr, useAction } from "@/components/ui";

type Adm = { id: string; patientId: string; patientName: string; encounterId: string; facilityId: string; admissionNumber: string; status: string; admittingDiagnosis?: string; admittedAt: string; dischargeType?: string; dischargeSummary?: string; dischargedAt?: string; currentBed?: string; currentWard?: string; lengthOfStayDays: number;
  history: { bedLabel: string; wardName: string; assignedAt: string; releasedAt?: string; reason?: string }[] };
type Ward = { id: string; name: string };
type Bed = { id: string; label: string; status: string };

export default function Admission({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can, facilityId } = useSession();
  const a = useFetch<Adm>(`/v1/inpatient/admissions/${id}`);
  const wards = useFetch<Ward[]>(a.data?.status === "ADMITTED" ? `/v1/inpatient/wards?facilityId=${facilityId}` : null);
  const [wardId, setWardId] = useState("");
  const ward = wardId || wards.data?.[0]?.id || "";
  const beds = useFetch<Bed[]>(ward ? `/v1/inpatient/wards/${ward}/beds` : null);
  const [t, setT] = useState({ bedId: "", reason: "" });
  const [dc, setDc] = useState({ type: "DISCHARGED", summary: "", noDx: "" });
  const act = useAction();
  const go = (fn: () => Promise<unknown>) => act.run(async () => { await fn(); await a.reload(); });
  if (a.loading || !a.data) return <Page title="Admission">{a.error ? <ErrorNote error={a.error} /> : <Loading />}</Page>;
  const d = a.data;
  const free = (beds.data ?? []).filter((b) => b.status === "AVAILABLE");
  return (
    <Page title={d.admissionNumber} sub={`${d.patientName} · admitted ${stamp(d.admittedAt)} · ${d.lengthOfStayDays} day(s)`} actions={<>
      <Status value={d.status} />
      <Button variant="secondary" href={`/encounters/${d.encounterId}`}>Open chart</Button>
    </>}>
      <ErrorNote error={act.error} />
      <Grid cols={3}><KV k="Bed" v={d.currentBed ? `${d.currentWard} ${d.currentBed}` : ""} /><KV k="Admitting diagnosis" v={d.admittingDiagnosis} /><KV k="Outcome" v={d.dischargeType} /></Grid>
      {d.dischargeSummary && <Card title="Discharge summary"><p className="whitespace-pre-wrap text-xs">{d.dischargeSummary}</p></Card>}
      <Card title="Bed history" pad={false}>
        <Table head={["Ward", "Bed", "From", "To", "Reason"]} empty="">{d.history.map((h, n) => <Tr key={n}><Td>{h.wardName}</Td><Td>{h.bedLabel}</Td><Td>{stamp(h.assignedAt)}</Td><Td>{stamp(h.releasedAt)}</Td><Td>{h.reason}</Td></Tr>)}</Table>
      </Card>
      {d.status === "ADMITTED" && can("inpatient:write") && (
        <Grid cols={2}>
          <Card title="Transfer">
            <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); void go(() => post(`/v1/inpatient/admissions/${id}/transfer`, { toBedId: t.bedId || free[0]?.id, reason: t.reason })); }}>
              <Field label="Ward"><Select value={ward} onChange={(e) => setWardId(e.target.value)}>{(wards.data ?? []).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</Select></Field>
              <Field label="Bed"><Select value={t.bedId || free[0]?.id || ""} onChange={(e) => setT({ ...t, bedId: e.target.value })}>{free.map((b) => <option key={b.id} value={b.id}>{b.label}</option>)}</Select></Field>
              <Field label="Reason"><Input required minLength={3} value={t.reason} onChange={(e) => setT({ ...t, reason: e.target.value })} /></Field>
              <Button type="submit" busy={act.busy} disabled={free.length === 0}>Transfer</Button>
            </form>
          </Card>
          <Card title="Discharge">
            <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); void go(() => post(`/v1/inpatient/admissions/${id}/discharge`, { type: dc.type, summary: dc.summary, noDiagnosisReason: dc.noDx || undefined })); }}>
              <Field label="Outcome"><Select value={dc.type} onChange={(e) => setDc({ ...dc, type: e.target.value })}>{["DISCHARGED", "REFERRED", "LAMA", "ABSCONDED", "DIED"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
              <Field label="Summary"><Textarea rows={4} required minLength={10} value={dc.summary} onChange={(e) => setDc({ ...dc, summary: e.target.value })} /></Field>
              <Field label="No primary diagnosis? Say why" hint="Needed only if no primary diagnosis is recorded on the chart"><Input value={dc.noDx} onChange={(e) => setDc({ ...dc, noDx: e.target.value })} /></Field>
              <Button type="submit" variant="danger" busy={act.busy}>Discharge</Button>
            </form>
          </Card>
        </Grid>
      )}
    </Page>
  );
}

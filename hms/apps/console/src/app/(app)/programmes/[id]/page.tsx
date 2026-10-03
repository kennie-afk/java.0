"use client";

import { use, useState } from "react";
import { post, useFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, Confirm, ErrorNote, Field, Grid, Input, KV, Loading, Page, Select, Status, Table, Td, Textarea, Tr, useAction } from "@/components/ui";

type Visit = { id: string; visitedOn: string; weightKg?: number; systolic?: number; diastolic?: number; glucoseMmol?: number; adherence?: string; regimen?: string; nextVisitOn?: string; notes?: string };
type Enrolment = { id: string; patientName: string; programme: string; registerNo: string; enrolledOn: string; status: string; regimen?: string; nextVisitOn?: string; outcomeOn?: string; outcomeNote?: string; daysOverdue?: number; visits: Visit[] };

const today = () => new Date().toISOString().slice(0, 10);

export default function EnrolmentPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const e = useFetch<Enrolment>(`/v1/programmes/enrolments/${id}`);
  const blank = { visitedOn: today(), weightKg: "", systolic: "", diastolic: "", glucoseMmol: "", adherence: "", regimen: "", nextVisitOn: "", notes: "" };
  const [v, setV] = useState(blank);
  const [out, setOut] = useState({ status: "TRANSFERRED_OUT", note: "" });
  const act = useAction();
  const num = (s: string) => (s === "" ? undefined : Number(s));
  const set = (k: keyof typeof blank) => (ev: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setV({ ...v, [k]: ev.target.value });
  if (e.loading || !e.data) return <Page title="Enrolment">{e.error ? <ErrorNote error={e.error} /> : <Loading />}</Page>;
  const d = e.data;
  const active = d.status === "ACTIVE";
  return (
    <Page title={d.registerNo} sub={`${d.patientName} · ${d.programme} · enrolled ${d.enrolledOn}`} actions={<Status value={d.status} />}>
      <Card><Grid cols={3}>
        <KV k="Regimen" v={d.regimen ?? "-"} />
        <KV k="Next visit" v={d.nextVisitOn ? `${d.nextVisitOn}${d.daysOverdue ? ` (${d.daysOverdue} days late)` : ""}` : "-"} />
        <KV k="Outcome" v={d.outcomeOn ? `${d.status} on ${d.outcomeOn}: ${d.outcomeNote}` : "-"} />
      </Grid></Card>
      {active && can("programmes:write") && (
        <Card title="Record a visit">
          <form className="space-y-3" onSubmit={(ev) => { ev.preventDefault(); void act.run(async () => { await post(`/v1/programmes/enrolments/${id}/visits`, { visitedOn: v.visitedOn, weightKg: num(v.weightKg), systolic: num(v.systolic), diastolic: num(v.diastolic), glucoseMmol: num(v.glucoseMmol), adherence: v.adherence || undefined, regimen: v.regimen || undefined, nextVisitOn: v.nextVisitOn || undefined, notes: v.notes || undefined }); setV(blank); await e.reload(); }); }}>
            <Grid cols={4}>
              <Field label="Visit date"><Input type="date" required value={v.visitedOn} onChange={set("visitedOn")} /></Field>
              <Field label="Weight (kg)"><Input type="number" step="0.1" value={v.weightKg} onChange={set("weightKg")} /></Field>
              <Field label="Systolic"><Input type="number" value={v.systolic} onChange={set("systolic")} /></Field>
              <Field label="Diastolic"><Input type="number" value={v.diastolic} onChange={set("diastolic")} /></Field>
              <Field label="Glucose (mmol/L)"><Input type="number" step="0.1" value={v.glucoseMmol} onChange={set("glucoseMmol")} /></Field>
              <Field label="Adherence"><Select value={v.adherence} onChange={set("adherence")}><option value="">-</option><option>GOOD</option><option>FAIR</option><option>POOR</option></Select></Field>
              <Field label="Regimen change"><Input value={v.regimen} onChange={set("regimen")} /></Field>
              <Field label="Next visit"><Input type="date" value={v.nextVisitOn} onChange={set("nextVisitOn")} /></Field>
            </Grid>
            <Field label="Notes"><Textarea rows={2} value={v.notes} onChange={set("notes")} /></Field>
            <ErrorNote error={act.error} />
            <Button type="submit" busy={act.busy}>Save visit</Button>
          </form>
        </Card>
      )}
      <Card title="Visits" pad={false}>
        <Table head={["Date", "Weight", "BP", "Glucose", "Adherence", "Next visit", "Notes"]} empty="No visits recorded yet.">
          {d.visits.map((x) => <Tr key={x.id}><Td>{x.visitedOn}</Td><Td>{x.weightKg}</Td><Td>{x.systolic ? `${x.systolic}/${x.diastolic}` : ""}</Td><Td>{x.glucoseMmol}</Td><Td>{x.adherence}</Td><Td>{x.nextVisitOn}</Td><Td>{x.notes}</Td></Tr>)}
        </Table>
      </Card>
      {active && can("programmes:write") && (
        <Card title="Record an outcome">
          <div className="flex flex-wrap items-end gap-2">
            <Field label="Outcome"><Select value={out.status} onChange={(ev) => setOut({ ...out, status: ev.target.value })}>{["TRANSFERRED_OUT", "LOST_TO_FOLLOW_UP", "COMPLETED", "DIED", "STOPPED"].map((s) => <option key={s}>{s}</option>)}</Select></Field>
            <Field label="Note"><Input value={out.note} onChange={(ev) => setOut({ ...out, note: ev.target.value })} /></Field>
            <Confirm label="Close enrolment" prompt="Close this enrolment with this outcome? A new enrolment is needed to resume." onConfirm={async () => { await act.run(async () => { await post(`/v1/programmes/enrolments/${id}/outcome`, out); await e.reload(); }); }} />
          </div>
        </Card>
      )}
    </Page>
  );
}

"use client";

import { use, useState } from "react";
import { post, useFetch } from "@/lib/api";
import { date, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, ErrorNote, Field, Grid, Input, KV, Loading, Page, Select, Table, Td, Textarea, Tr, useAction } from "@/components/ui";

type Flag = { code: string; level: string; message: string };
type Visit = { id: string; visitedOn: string; visitType: string; method: string; methodLabel: string; systolic?: number; diastolic?: number; weightKg?: number; nextDueOn?: string; flags: Flag[]; notes?: string };
type Plan = { patientName: string; currentMethod?: string; currentMethodLabel?: string; nextDueOn?: string; overdue: boolean; visits: Visit[] };

const METHODS: [string, string][] = [["COC", "Combined oral pill"], ["POP", "Progestogen-only pill"], ["DMPA", "Injectable (DMPA)"], ["IMPLANT", "Implant"], ["IUCD", "Intrauterine device"], ["MALE_CONDOM", "Male condom"], ["FEMALE_CONDOM", "Female condom"], ["TUBAL_LIGATION", "Tubal ligation"], ["VASECTOMY", "Vasectomy"], ["NATURAL", "Natural methods"], ["EMERGENCY", "Emergency contraception"]];
const PERMANENT = ["TUBAL_LIGATION", "VASECTOMY"];
const num = (s: string) => (s === "" ? undefined : Number(s));

export default function FamilyPlanningRecord({ params }: { params: Promise<{ patientId: string }> }) {
  const { patientId } = use(params);
  const { facilityId, can } = useSession();
  const f = useFetch<Plan>(`/v1/mch/family-planning/patients/${patientId}`);
  const [v, setV] = useState({ visitType: "", method: "", visitedOn: today(), systolic: "", diastolic: "", weightKg: "", nextDueOn: "", notes: "" });
  const act = useAction();
  if (f.loading || !f.data) return <Page title="Family planning">{f.error ? <ErrorNote error={f.error} /> : <Loading />}</Page>;
  const x = f.data;
  const type = v.visitType || (x.currentMethod ? "REVISIT" : "NEW");
  const method = type === "DISCONTINUE" ? "NONE" : type === "REVISIT" ? x.currentMethod! : v.method || METHODS[0][0];
  const types = x.currentMethod ? [["REVISIT", "Revisit (same method)"], ["SWITCH", "Switch method"], ["DISCONTINUE", "Stop method"]] : [["NEW", "New start"]];
  return (
    <Page title={x.patientName} sub="Family planning" actions={<Button variant="secondary" href={`/patients/${patientId}`}>Patient record</Button>}>
      <ErrorNote error={act.error} />
      <Grid cols={3}><KV k="Current method" v={x.currentMethodLabel ?? "None on record"} /><KV k="Next contact" v={<>{date(x.nextDueOn)}{x.overdue && <> <Badge tone="warn">Overdue</Badge></>}</>} /><KV k="Visits" v={x.visits.length} /></Grid>
      <Card title="Visits" pad={false}>
        <Table head={["Date", "Type", "Method", "BP", "Weight", "Next contact", "Flags", "Notes"]} empty="No visits recorded.">
          {x.visits.map((r) => <Tr key={r.id} tone={r.flags.some((g) => g.level === "DANGER") ? "danger" : r.flags.length ? "warn" : undefined}><Td>{date(r.visitedOn)}</Td><Td>{r.visitType}</Td><Td>{r.methodLabel}</Td><Td>{r.systolic && `${r.systolic}/${r.diastolic}`}</Td><Td>{r.weightKg}</Td><Td>{date(r.nextDueOn)}</Td><Td>{r.flags.map((g) => g.message).join("; ")}</Td><Td>{r.notes}</Td></Tr>)}
        </Table>
      </Card>
      {can("mch:write") && (
        <Card title="Record visit">
          <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); void act.run(async () => {
            await post(`/v1/mch/family-planning/patients/${patientId}/visits`, { facilityId, visitType: type, method, visitedOn: v.visitedOn, systolic: num(v.systolic), diastolic: num(v.diastolic), weightKg: num(v.weightKg), nextDueOn: v.nextDueOn || undefined, notes: v.notes || undefined });
            setV({ ...v, visitType: "", method: "", systolic: "", diastolic: "", weightKg: "", nextDueOn: "", notes: "" });
            await f.reload(); }); }}>
            <Grid cols={4}>
              <Field label="Visit type"><Select value={type} onChange={(e) => setV({ ...v, visitType: e.target.value })}>{types.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</Select></Field>
              {(type === "NEW" || type === "SWITCH") && <Field label="Method"><Select value={method} onChange={(e) => setV({ ...v, method: e.target.value })}>{METHODS.filter(([k]) => k !== x.currentMethod).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</Select></Field>}
              <Field label="Date"><Input type="date" required max={today()} value={v.visitedOn} onChange={(e) => setV({ ...v, visitedOn: e.target.value })} /></Field>
              <Field label="Systolic"><Input type="number" value={v.systolic} onChange={(e) => setV({ ...v, systolic: e.target.value })} /></Field>
              <Field label="Diastolic"><Input type="number" value={v.diastolic} onChange={(e) => setV({ ...v, diastolic: e.target.value })} /></Field>
              <Field label="Weight (kg)"><Input type="number" step="0.1" value={v.weightKg} onChange={(e) => setV({ ...v, weightKg: e.target.value })} /></Field>
              {type !== "DISCONTINUE" && !PERMANENT.includes(method) && <Field label="Next contact" hint={method === "DMPA" ? "Left blank, set 13 weeks on" : undefined}><Input type="date" min={v.visitedOn} value={v.nextDueOn} onChange={(e) => setV({ ...v, nextDueOn: e.target.value })} /></Field>}
            </Grid>
            <Field label="Notes"><Textarea rows={2} value={v.notes} onChange={(e) => setV({ ...v, notes: e.target.value })} /></Field>
            <Button type="submit" busy={act.busy}>Save visit</Button>
          </form>
        </Card>
      )}
    </Page>
  );
}

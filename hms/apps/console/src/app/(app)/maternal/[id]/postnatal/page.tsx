"use client";

import { use, useState } from "react";
import { post, useFetch } from "@/lib/api";
import { date, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, ErrorNote, Field, Grid, Input, KV, Loading, Notice, Page, Select, Table, Td, Textarea, Tr, useAction } from "@/components/ui";

type Flag = { code: string; level: string; message: string };
type Visit = { id: string; visitNumber: number; visitedOn: string; daysSinceDelivery: number; systolic?: number; diastolic?: number; temperatureC?: number; uterus?: string; lochia?: string; wound?: string; breastfeeding?: string; lowMood: boolean; babyWeightG?: number; babyTemperatureC?: number; cord?: string; jaundice?: boolean; feedingWell?: boolean; flags: Flag[]; nextVisitOn?: string };
type Postnatal = { patientId: string; patientName: string; deliveredOn: string; outcome: string; babies: number; daysSinceDelivery: number; babyRecorded: boolean; nextVisitOn?: string; overdue: boolean; scheduleNote: string; visits: Visit[] };

const num = (s: string) => (s === "" ? undefined : Number(s));
const tone = (l: string) => (l === "DANGER" ? "danger" : "warn") as "danger" | "warn";
const yn = (s: string) => (s === "" ? undefined : s === "yes");

export default function PostnatalCare({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const p = useFetch<Postnatal>(`/v1/mch/pregnancies/${id}/postnatal`);
  const [v, setV] = useState({ visitedOn: today(), systolic: "", diastolic: "", temperatureC: "", uterus: "NOT_ASSESSED", lochia: "NOT_ASSESSED", wound: "NOT_ASSESSED", breastfeeding: "NOT_ASSESSED", lowMood: false, fpCounselled: false,
    babyWeightG: "", babyTemperatureC: "", cord: "NOT_ASSESSED", jaundice: "", feedingWell: "", notes: "", nextVisitOn: "" });
  const act = useAction();
  if (p.loading || !p.data) return <Page title="Postnatal care">{p.error ? <ErrorNote error={p.error} /> : <Loading />}</Page>;
  const x = p.data;
  const flags = x.visits.length ? x.visits[x.visits.length - 1].flags : [];
  return (
    <Page title={x.patientName} sub="Postnatal care" actions={<Button variant="secondary" href={`/maternal/${id}`}>Pregnancy record</Button>}>
      <ErrorNote error={act.error} />
      <Grid cols={4}><KV k="Delivered" v={date(x.deliveredOn)} /><KV k="Days since" v={x.daysSinceDelivery} /><KV k="Outcome" v={x.outcome} /><KV k="Next visit" v={<>{date(x.nextVisitOn)}{x.overdue && <> <Badge tone="warn">Overdue</Badge></>}</>} /></Grid>
      <Notice title="Schedule">{x.scheduleNote}</Notice>
      {flags.length > 0 && <Card title="Needs attention (latest visit)">{flags.map((f) => <p key={f.code} className="flex items-center gap-2 py-0.5 text-sm"><Badge tone={tone(f.level)}>{f.level}</Badge>{f.message}</p>)}</Card>}
      <Card title="Visits" pad={false}>
        <Table head={["#", "Date", "Day", "BP", "Temp", "Lochia", "Uterus", "Wound", "Feeding", "Baby kg", "Baby temp", "Cord", "Flags"]} empty="No postnatal visits recorded.">
          {x.visits.map((r) => <Tr key={r.id} tone={r.flags.some((f) => f.level === "DANGER") ? "danger" : undefined}><Td>{r.visitNumber}</Td><Td>{date(r.visitedOn)}</Td><Td>{r.daysSinceDelivery}</Td><Td>{r.systolic && `${r.systolic}/${r.diastolic}`}</Td><Td>{r.temperatureC}</Td><Td>{r.lochia}</Td><Td>{r.uterus}</Td><Td>{r.wound}</Td><Td>{r.breastfeeding}</Td><Td>{r.babyWeightG && (r.babyWeightG / 1000).toFixed(2)}</Td><Td>{r.babyTemperatureC}</Td><Td>{r.cord}</Td><Td>{r.flags.map((f) => f.message).join("; ")}</Td></Tr>)}
        </Table>
      </Card>
      {can("mch:write") && (
        <Card title="Record postnatal visit">
          <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); void act.run(async () => {
            await post(`/v1/mch/pregnancies/${id}/postnatal`, { visitedOn: v.visitedOn, systolic: num(v.systolic), diastolic: num(v.diastolic), temperatureC: num(v.temperatureC), uterus: v.uterus, lochia: v.lochia, wound: v.wound, breastfeeding: v.breastfeeding, lowMood: v.lowMood, fpCounselled: v.fpCounselled,
              ...(x.babyRecorded ? { babyWeightG: num(v.babyWeightG), babyTemperatureC: num(v.babyTemperatureC), cord: v.cord, jaundice: yn(v.jaundice), feedingWell: yn(v.feedingWell) } : {}), notes: v.notes || undefined, nextVisitOn: v.nextVisitOn || undefined });
            await p.reload(); }); }}>
            <p className="text-xs font-medium uppercase tracking-wide text-muted">Mother</p>
            <Grid cols={4}>
              <Field label="Date"><Input type="date" required min={x.deliveredOn} max={today()} value={v.visitedOn} onChange={(e) => setV({ ...v, visitedOn: e.target.value })} /></Field>
              <Field label="Systolic"><Input type="number" value={v.systolic} onChange={(e) => setV({ ...v, systolic: e.target.value })} /></Field>
              <Field label="Diastolic"><Input type="number" value={v.diastolic} onChange={(e) => setV({ ...v, diastolic: e.target.value })} /></Field>
              <Field label="Temperature (C)"><Input type="number" step="0.1" value={v.temperatureC} onChange={(e) => setV({ ...v, temperatureC: e.target.value })} /></Field>
              <Field label="Uterus"><Select value={v.uterus} onChange={(e) => setV({ ...v, uterus: e.target.value })}>{["NOT_ASSESSED", "INVOLUTING", "SUBINVOLUTED"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
              <Field label="Lochia"><Select value={v.lochia} onChange={(e) => setV({ ...v, lochia: e.target.value })}>{["NOT_ASSESSED", "NORMAL", "HEAVY", "OFFENSIVE"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
              <Field label="Wound"><Select value={v.wound} onChange={(e) => setV({ ...v, wound: e.target.value })}>{["NOT_ASSESSED", "HEALED", "INFECTED", "NOT_APPLICABLE"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
              <Field label="Breastfeeding"><Select value={v.breastfeeding} onChange={(e) => setV({ ...v, breastfeeding: e.target.value })}>{["NOT_ASSESSED", "EXCLUSIVE", "MIXED", "NOT_BREASTFEEDING"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
            </Grid>
            <div className="flex flex-wrap gap-4 text-sm">
              <label className="flex items-center gap-1.5"><input type="checkbox" checked={v.lowMood} onChange={(e) => setV({ ...v, lowMood: e.target.checked })} />Low mood reported</label>
              <label className="flex items-center gap-1.5"><input type="checkbox" checked={v.fpCounselled} onChange={(e) => setV({ ...v, fpCounselled: e.target.checked })} />Family planning counselling given</label>
            </div>
            {x.babyRecorded && <>
              <p className="pt-2 text-xs font-medium uppercase tracking-wide text-muted">Baby</p>
              <Grid cols={4}>
                <Field label="Weight (g)"><Input type="number" value={v.babyWeightG} onChange={(e) => setV({ ...v, babyWeightG: e.target.value })} /></Field>
                <Field label="Temperature (C)"><Input type="number" step="0.1" value={v.babyTemperatureC} onChange={(e) => setV({ ...v, babyTemperatureC: e.target.value })} /></Field>
                <Field label="Cord"><Select value={v.cord} onChange={(e) => setV({ ...v, cord: e.target.value })}>{["NOT_ASSESSED", "CLEAN", "INFECTED", "SEPARATED"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
                <Field label="Jaundice"><Select value={v.jaundice} onChange={(e) => setV({ ...v, jaundice: e.target.value })}><option value="">Not assessed</option><option value="no">No</option><option value="yes">Yes</option></Select></Field>
                <Field label="Feeding well"><Select value={v.feedingWell} onChange={(e) => setV({ ...v, feedingWell: e.target.value })}><option value="">Not assessed</option><option value="yes">Yes</option><option value="no">No</option></Select></Field>
              </Grid>
            </>}
            <Field label="Notes"><Textarea rows={2} value={v.notes} onChange={(e) => setV({ ...v, notes: e.target.value })} /></Field>
            <Field label="Next visit"><Input type="date" min={today()} value={v.nextVisitOn} onChange={(e) => setV({ ...v, nextVisitOn: e.target.value })} /></Field>
            <Button type="submit" busy={act.busy}>Save visit</Button>
          </form>
        </Card>
      )}
    </Page>
  );
}

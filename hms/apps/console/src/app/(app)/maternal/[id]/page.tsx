"use client";

import { use, useState } from "react";
import { post, useFetch } from "@/lib/api";
import { date, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, ErrorNote, Field, Grid, Input, KV, Loading, Page, Select, Status, Table, Td, Textarea, Tr, useAction } from "@/components/ui";

type Flag = { code: string; level: string; message: string };
type Visit = { id: string; visitNumber: number; visitedOn: string; gestationWeeks: number; weightKg?: number; systolic?: number; diastolic?: number; fundalHeightCm?: number; fetalHeartRate?: number; presentation?: string; haemoglobin?: number; hivStatus?: string; syphilis?: string; urineProtein?: string; flags: Flag[]; nextVisitOn?: string };
type Preg = { id: string; patientId: string; patientName: string; lmp: string; edd: string; gestationWeeks: number; gestationDays: number; gravida: number; parity: number; status: string; overdue: boolean; nextVisitOn?: string; flags: Flag[]; visits: Visit[];
  delivery?: { deliveredOn: string; gestationWeeks: number; mode: string; outcome: string; babies: number; birthWeightG?: number; apgar5?: number; bloodLossMl?: number; complications?: string } };

const num = (s: string) => (s === "" ? undefined : Number(s));
const tone = (l: string) => (l === "DANGER" ? "danger" : "warn") as "danger" | "warn";

export default function Pregnancy({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const p = useFetch<Preg>(`/v1/mch/pregnancies/${id}`);
  const [v, setV] = useState({ weightKg: "", systolic: "", diastolic: "", fundalHeightCm: "", fetalHeartRate: "", presentation: "NOT_ASSESSED", haemoglobin: "", hivStatus: "NOT_TESTED", syphilis: "NOT_TESTED", urineProtein: "NOT_TESTED", iptpGiven: false, tetanusGiven: false, ironFolateGiven: false, notes: "", nextVisitOn: "" });
  const [d, setD] = useState({ deliveredOn: today(), mode: "SVD", outcome: "LIVE_BIRTH", babies: "1", birthWeightG: "", apgar5: "", bloodLossMl: "", complications: "" });
  const act = useAction();
  const go = (fn: () => Promise<unknown>) => act.run(async () => { await fn(); await p.reload(); });
  if (p.loading || !p.data) return <Page title="Pregnancy">{p.error ? <ErrorNote error={p.error} /> : <Loading />}</Page>;
  const x = p.data;
  const active = x.status === "ACTIVE";
  return (
    <Page title={x.patientName} sub={`${x.gestationWeeks}w ${x.gestationDays}d · G${x.gravida} P${x.parity}`} actions={<><Status value={x.status} /><Button variant="secondary" href={`/patients/${x.patientId}`}>Patient record</Button></>}>
      <ErrorNote error={act.error} />
      <Grid cols={4}><KV k="LMP" v={date(x.lmp)} /><KV k="Expected delivery" v={date(x.edd)} /><KV k="Next visit" v={<>{date(x.nextVisitOn)}{x.overdue && <> <Badge tone="warn">Overdue</Badge></>}</>} /><KV k="Visits" v={x.visits.length} /></Grid>
      {x.flags.length > 0 && <Card title="Needs attention">{x.flags.map((f) => <p key={f.code} className="flex items-center gap-2 py-0.5 text-sm"><Badge tone={tone(f.level)}>{f.level}</Badge>{f.message}</p>)}</Card>}
      <Card title="Antenatal visits" pad={false}>
        <Table head={["#", "Date", "Weeks", "Weight", "BP", "Fundal", "FHR", "Hb", "HIV", "Syphilis", "Protein", "Flags"]} empty="No visits recorded.">
          {x.visits.map((r) => <Tr key={r.id} tone={r.flags.some((f) => f.level === "DANGER") ? "danger" : undefined}><Td>{r.visitNumber}</Td><Td>{date(r.visitedOn)}</Td><Td>{r.gestationWeeks}</Td><Td>{r.weightKg}</Td><Td>{r.systolic && `${r.systolic}/${r.diastolic}`}</Td><Td>{r.fundalHeightCm}</Td><Td>{r.fetalHeartRate}</Td><Td>{r.haemoglobin}</Td><Td>{r.hivStatus}</Td><Td>{r.syphilis}</Td><Td>{r.urineProtein}</Td><Td>{r.flags.map((f) => f.message).join("; ")}</Td></Tr>)}
        </Table>
      </Card>
      {x.delivery && <Card title="Delivery"><Grid cols={4}><KV k="Date" v={date(x.delivery.deliveredOn)} /><KV k="Gestation" v={`${x.delivery.gestationWeeks} weeks`} /><KV k="Mode" v={x.delivery.mode} /><KV k="Outcome" v={x.delivery.outcome} /><KV k="Babies" v={x.delivery.babies} /><KV k="Birth weight" v={x.delivery.birthWeightG && `${x.delivery.birthWeightG} g`} /><KV k="Apgar at 5 min" v={x.delivery.apgar5} /><KV k="Blood loss" v={x.delivery.bloodLossMl && `${x.delivery.bloodLossMl} ml`} /></Grid>{x.delivery.complications && <p className="pt-3 text-sm">{x.delivery.complications}</p>}</Card>}
      {active && can("mch:write") && (
        <Grid cols={2}>
          <Card title="Record antenatal visit">
            <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); void go(() => post(`/v1/mch/pregnancies/${id}/visits`, { weightKg: num(v.weightKg), systolic: num(v.systolic), diastolic: num(v.diastolic), fundalHeightCm: num(v.fundalHeightCm), fetalHeartRate: num(v.fetalHeartRate), presentation: v.presentation, haemoglobin: num(v.haemoglobin), hivStatus: v.hivStatus, syphilis: v.syphilis, urineProtein: v.urineProtein, iptpGiven: v.iptpGiven, tetanusGiven: v.tetanusGiven, ironFolateGiven: v.ironFolateGiven, notes: v.notes || undefined, nextVisitOn: v.nextVisitOn || undefined })); }}>
              <Grid cols={2}>
                <Field label="Weight (kg)"><Input type="number" step="0.1" value={v.weightKg} onChange={(e) => setV({ ...v, weightKg: e.target.value })} /></Field>
                <Field label="Haemoglobin (g/dL)"><Input type="number" step="0.1" value={v.haemoglobin} onChange={(e) => setV({ ...v, haemoglobin: e.target.value })} /></Field>
                <Field label="Systolic"><Input type="number" value={v.systolic} onChange={(e) => setV({ ...v, systolic: e.target.value })} /></Field>
                <Field label="Diastolic"><Input type="number" value={v.diastolic} onChange={(e) => setV({ ...v, diastolic: e.target.value })} /></Field>
                <Field label="Fundal height (cm)"><Input type="number" step="0.5" value={v.fundalHeightCm} onChange={(e) => setV({ ...v, fundalHeightCm: e.target.value })} /></Field>
                <Field label="Fetal heart rate"><Input type="number" value={v.fetalHeartRate} onChange={(e) => setV({ ...v, fetalHeartRate: e.target.value })} /></Field>
                <Field label="Presentation"><Select value={v.presentation} onChange={(e) => setV({ ...v, presentation: e.target.value })}>{["NOT_ASSESSED", "CEPHALIC", "BREECH", "TRANSVERSE"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
                <Field label="Urine protein"><Select value={v.urineProtein} onChange={(e) => setV({ ...v, urineProtein: e.target.value })}>{["NOT_TESTED", "NEGATIVE", "TRACE", "1+", "2+", "3+"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
                <Field label="HIV"><Select value={v.hivStatus} onChange={(e) => setV({ ...v, hivStatus: e.target.value })}>{["NOT_TESTED", "NEGATIVE", "POSITIVE", "KNOWN_POSITIVE"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
                <Field label="Syphilis"><Select value={v.syphilis} onChange={(e) => setV({ ...v, syphilis: e.target.value })}>{["NOT_TESTED", "NEGATIVE", "REACTIVE"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
              </Grid>
              <div className="flex flex-wrap gap-4 text-sm">
                {([["iptpGiven", "IPTp given"], ["tetanusGiven", "Tetanus given"], ["ironFolateGiven", "Iron and folate given"]] as const).map(([k, l]) => <label key={k} className="flex items-center gap-1.5"><input type="checkbox" checked={v[k]} onChange={(e) => setV({ ...v, [k]: e.target.checked })} />{l}</label>)}
              </div>
              <Field label="Notes"><Textarea rows={2} value={v.notes} onChange={(e) => setV({ ...v, notes: e.target.value })} /></Field>
              <Field label="Next visit"><Input type="date" min={today()} value={v.nextVisitOn} onChange={(e) => setV({ ...v, nextVisitOn: e.target.value })} /></Field>
              <Button type="submit" busy={act.busy}>Save visit</Button>
            </form>
          </Card>
          <Card title="Record delivery">
            <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); void go(() => post(`/v1/mch/pregnancies/${id}/delivery`, { deliveredOn: d.deliveredOn, mode: d.mode, outcome: d.outcome, babies: num(d.babies), birthWeightG: num(d.birthWeightG), apgar5: num(d.apgar5), bloodLossMl: num(d.bloodLossMl), complications: d.complications || undefined })); }}>
              <Grid cols={2}>
                <Field label="Date"><Input type="date" required max={today()} value={d.deliveredOn} onChange={(e) => setD({ ...d, deliveredOn: e.target.value })} /></Field>
                <Field label="Mode"><Select value={d.mode} onChange={(e) => setD({ ...d, mode: e.target.value })}>{["SVD", "ASSISTED_VAGINAL", "CAESAREAN", "BREECH", "NOT_APPLICABLE"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
                <Field label="Outcome"><Select value={d.outcome} onChange={(e) => setD({ ...d, outcome: e.target.value })}>{["LIVE_BIRTH", "STILLBIRTH", "MISCARRIAGE"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
                <Field label="Babies"><Input type="number" min={0} max={5} value={d.babies} onChange={(e) => setD({ ...d, babies: e.target.value })} /></Field>
                <Field label="Birth weight (g)"><Input type="number" value={d.birthWeightG} onChange={(e) => setD({ ...d, birthWeightG: e.target.value })} /></Field>
                <Field label="Apgar at 5 min"><Input type="number" min={0} max={10} value={d.apgar5} onChange={(e) => setD({ ...d, apgar5: e.target.value })} /></Field>
                <Field label="Blood loss (ml)"><Input type="number" value={d.bloodLossMl} onChange={(e) => setD({ ...d, bloodLossMl: e.target.value })} /></Field>
              </Grid>
              <Field label="Complications"><Textarea rows={2} value={d.complications} onChange={(e) => setD({ ...d, complications: e.target.value })} /></Field>
              <Button type="submit" variant="danger" busy={act.busy}>Record delivery and close</Button>
            </form>
          </Card>
        </Grid>
      )}
    </Page>
  );
}

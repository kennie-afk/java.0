"use client";

import { use, useState } from "react";
import { post, useFetch } from "@/lib/api";
import { date, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Field, Grid, Input, KV, Loading, Notice, Page, Select, Status, Table, Td, Tr, useAction } from "@/components/ui";

type Dose = { vaccine: string; label: string; antigen: string; dueAge: string; dueOn: string; status: string; givenOn?: string; batchNo?: string; site?: string };
type Card_ = { patientName: string; birthDate: string; ageLabel: string; given: number; total: number; doses: Dose[]; scheduleNote?: string };

export default function ImmunisationCard({ params }: { params: Promise<{ patientId: string }> }) {
  const { patientId } = use(params);
  const { facilityId, can } = useSession();
  const c = useFetch<Card_>(`/v1/mch/immunisation/patients/${patientId}`);
  const [f, setF] = useState({ vaccine: "", givenOn: today(), batchNo: "", site: "" });
  const act = useAction();
  if (c.loading || !c.data) return <Page title="Immunisation card">{c.error ? <ErrorNote error={c.error} /> : <Loading />}</Page>;
  const x = c.data;
  const pending = x.doses.filter((d) => d.status !== "GIVEN");
  const vaccine = f.vaccine || pending[0]?.vaccine || "";
  return (
    <Page title={x.patientName} sub={`${x.ageLabel} · born ${date(x.birthDate)}`} actions={<Button variant="secondary" href={`/patients/${patientId}`}>Patient record</Button>}>
      <Grid cols={3}><KV k="Doses given" v={`${x.given} of ${x.total}`} /><KV k="Born" v={date(x.birthDate)} /><KV k="Age" v={x.ageLabel} /></Grid>
      {x.scheduleNote && <Notice title="Schedule">{x.scheduleNote}</Notice>}
      <Card pad={false}>
        <Table head={["Dose", "Antigen", "Due age", "Due", "Status", "Given", "Batch", "Site"]} empty="">
          {x.doses.map((d) => <Tr key={d.vaccine} tone={d.status === "OVERDUE" ? "warn" : undefined}><Td>{d.label}</Td><Td>{d.antigen}</Td><Td>{d.dueAge}</Td><Td>{date(d.dueOn)}</Td><Td><Status value={d.status} /></Td><Td>{date(d.givenOn)}</Td><Td>{d.batchNo}</Td><Td>{d.site}</Td></Tr>)}
        </Table>
      </Card>
      {can("mch:write") && pending.length > 0 && (
        <Card title="Record a dose">
          <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); void act.run(async () => { await post(`/v1/mch/immunisation/patients/${patientId}/doses`, { facilityId, vaccine, givenOn: f.givenOn, batchNo: f.batchNo || undefined, site: f.site || undefined }); await c.reload(); }); }}>
            <Grid cols={4}>
              <Field label="Dose"><Select value={vaccine} onChange={(e) => setF({ ...f, vaccine: e.target.value })}>{pending.map((d) => <option key={d.vaccine} value={d.vaccine}>{d.label}</option>)}</Select></Field>
              <Field label="Date given"><Input type="date" required max={today()} value={f.givenOn} onChange={(e) => setF({ ...f, givenOn: e.target.value })} /></Field>
              <Field label="Batch number"><Input value={f.batchNo} onChange={(e) => setF({ ...f, batchNo: e.target.value })} /></Field>
              <Field label="Site"><Select value={f.site} onChange={(e) => setF({ ...f, site: e.target.value })}><option value="">Not recorded</option>{["LEFT_THIGH", "RIGHT_THIGH", "LEFT_ARM", "RIGHT_ARM", "ORAL"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
            </Grid>
            <ErrorNote error={act.error} /><Button type="submit" busy={act.busy}>Record dose</Button>
          </form>
        </Card>
      )}
    </Page>
  );
}

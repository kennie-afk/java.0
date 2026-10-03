"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, Textarea, useAction } from "@/components/ui";

type Measure = { code: string; label: string; description: string; filter: string; filterRequired: boolean; disaggregations: string[] };
type El = { code: string; label: string; measure: string; disaggregation: string; filter: string };

const blank = (n: number): El => ({ code: `E${n}`, label: "", measure: "OPD_VISITS", disaggregation: "NONE", filter: "" });

export default function NewReport() {
  const router = useRouter();
  const measures = useFetch<Measure[]>("/v1/report-definitions/measures");
  const [head, setHead] = useState({ code: "", name: "", description: "" });
  const [els, setEls] = useState<El[]>([blank(1)]);
  const { busy, error, run } = useAction();
  const m = (code: string) => measures.data?.find((x) => x.code === code);
  const patch = (i: number, p: Partial<El>) => setEls(els.map((e, j) => (j === i ? { ...e, ...p } : e)));
  return (
    <Page title="New custom report" sub="Each element counts one measure for the facility and period you choose. Nothing here is free-form SQL.">
      <form className="space-y-4" onSubmit={(ev) => { ev.preventDefault(); void run(async () => { const d = await post<{ id: string }>("/v1/report-definitions", { code: head.code.toUpperCase(), name: head.name, description: head.description || undefined, elements: els.map((e) => ({ ...e, filter: e.filter || undefined })) }); router.push("/reports/custom"); return d; }); }}>
        <Card><Grid cols={3}>
          <Field label="Code"><Input required value={head.code} onChange={(e) => setHead({ ...head, code: e.target.value })} /></Field>
          <Field label="Name"><Input required value={head.name} onChange={(e) => setHead({ ...head, name: e.target.value })} /></Field>
        </Grid>
        <Field label="Description"><Textarea rows={2} value={head.description} onChange={(e) => setHead({ ...head, description: e.target.value })} /></Field></Card>
        {els.map((e, i) => {
          const meas = m(e.measure);
          return (
            <Card key={i} title={`Element ${i + 1}`} actions={els.length > 1 ? <Button variant="secondary" onClick={() => setEls(els.filter((_, j) => j !== i))}>Remove</Button> : undefined}>
              <Grid cols={4}>
                <Field label="Code"><Input required value={e.code} onChange={(ev) => patch(i, { code: ev.target.value })} /></Field>
                <Field label="Label"><Input required value={e.label} onChange={(ev) => patch(i, { label: ev.target.value })} /></Field>
                <Field label="Measure" hint={meas?.description}><Select value={e.measure} onChange={(ev) => patch(i, { measure: ev.target.value, disaggregation: "NONE", filter: "" })}>{(measures.data ?? []).map((x) => <option key={x.code} value={x.code}>{x.label}</option>)}</Select></Field>
                <Field label="Split by"><Select value={e.disaggregation} onChange={(ev) => patch(i, { disaggregation: ev.target.value })}>{(meas?.disaggregations ?? ["NONE"]).map((d) => <option key={d}>{d}</option>)}</Select></Field>
                {meas && meas.filter !== "NONE" && <Field label={meas.filter === "ICD_PREFIX" ? "ICD-11 code starts with" : meas.filter === "PROGRAMME" ? "Programme" : "Vaccine"} hint={meas.filterRequired ? "Required" : "Optional"}><Input required={meas.filterRequired} value={e.filter} onChange={(ev) => patch(i, { filter: ev.target.value })} /></Field>}
              </Grid>
            </Card>
          );
        })}
        <div className="flex gap-2"><Button variant="secondary" onClick={() => setEls([...els, blank(els.length + 1)])}>Add element</Button><Button type="submit" busy={busy}>Save report</Button></div>
        <ErrorNote error={error} />
      </form>
    </Page>
  );
}

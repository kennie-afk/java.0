"use client";

import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { kes } from "@/lib/format";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, Table, Td, Tr, useAction } from "@/components/ui";

type Test = { id: string; code: string; name: string; resultType: string; unit?: string; refLow?: number; refHigh?: number; criticalLow?: number; criticalHigh?: number; price: number };

export default function Tests() {
  const tests = useFetch<Test[]>("/v1/lab/tests?activeOnly=false");
  const [f, setF] = useState({ code: "", name: "", resultType: "NUMERIC", unit: "", refLow: "", refHigh: "", criticalLow: "", criticalHigh: "", price: "" });
  const { busy, error, run } = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  const n = (s: string) => (s === "" ? undefined : Number(s));
  return (
    <Page title="Test catalogue" sub="Reference and critical ranges are one set for all ages and sexes.">
      <Card pad={false}>
        <Table head={["Code", "Test", "Type", "Unit", "Reference", "Critical", "Price"]} empty="No tests yet.">
          {(tests.data ?? []).map((t) => <Tr key={t.id}><Td>{t.code}</Td><Td>{t.name}</Td><Td>{t.resultType}</Td><Td>{t.unit}</Td><Td>{t.refLow}-{t.refHigh}</Td><Td>{t.criticalLow}/{t.criticalHigh}</Td><Td>{kes(t.price)}</Td></Tr>)}
        </Table>
      </Card>
      <Card title="Add a test">
        <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); void run(async () => { await post("/v1/lab/tests", { code: f.code.toUpperCase(), name: f.name, resultType: f.resultType, unit: f.unit || undefined, refLow: n(f.refLow), refHigh: n(f.refHigh), criticalLow: n(f.criticalLow), criticalHigh: n(f.criticalHigh), price: n(f.price) }); setF({ ...f, code: "", name: "" }); await tests.reload(); }); }}>
          <Grid cols={4}>
            <Field label="Code"><Input required value={f.code} onChange={set("code")} /></Field>
            <Field label="Name"><Input required value={f.name} onChange={set("name")} /></Field>
            <Field label="Result"><Select value={f.resultType} onChange={set("resultType")}><option>NUMERIC</option><option>TEXT</option></Select></Field>
            <Field label="Unit"><Input value={f.unit} onChange={set("unit")} /></Field>
            <Field label="Reference low"><Input type="number" step="any" value={f.refLow} onChange={set("refLow")} /></Field>
            <Field label="Reference high"><Input type="number" step="any" value={f.refHigh} onChange={set("refHigh")} /></Field>
            <Field label="Critical low"><Input type="number" step="any" value={f.criticalLow} onChange={set("criticalLow")} /></Field>
            <Field label="Critical high"><Input type="number" step="any" value={f.criticalHigh} onChange={set("criticalHigh")} /></Field>
            <Field label="Price (KES)"><Input type="number" step="0.01" value={f.price} onChange={set("price")} /></Field>
          </Grid>
          <ErrorNote error={error} />
          <Button type="submit" busy={busy}>Add test</Button>
        </form>
      </Card>
    </Page>
  );
}

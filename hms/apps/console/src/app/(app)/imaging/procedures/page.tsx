"use client";

import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { kes } from "@/lib/format";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, Table, Td, Tr, useAction } from "@/components/ui";

type Procedure = { id: string; code: string; name: string; modality: string; bodyRegion?: string; price: number; active: boolean };

export default function Procedures() {
  const list = useFetch<Procedure[]>("/v1/imaging/procedures?activeOnly=false");
  const [f, setF] = useState({ code: "", name: "", modality: "XR", bodyRegion: "", price: "" });
  const { busy, error, run } = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Page title="Imaging procedures">
      <Card pad={false}>
        <Table head={["Code", "Procedure", "Modality", "Region", "Price"]} empty="No procedures yet.">
          {(list.data ?? []).map((p) => <Tr key={p.id}><Td>{p.code}</Td><Td>{p.name}</Td><Td>{p.modality}</Td><Td>{p.bodyRegion}</Td><Td>{kes(p.price)}</Td></Tr>)}
        </Table>
      </Card>
      <Card title="Add a procedure">
        <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); void run(async () => { await post("/v1/imaging/procedures", { code: f.code.toUpperCase(), name: f.name, modality: f.modality, bodyRegion: f.bodyRegion || undefined, price: f.price === "" ? undefined : Number(f.price) }); setF({ ...f, code: "", name: "" }); await list.reload(); }); }}>
          <Grid cols={4}>
            <Field label="Code"><Input required value={f.code} onChange={set("code")} /></Field>
            <Field label="Name"><Input required value={f.name} onChange={set("name")} /></Field>
            <Field label="Modality"><Select value={f.modality} onChange={set("modality")}>{["XR", "US", "CT", "MR", "MG", "FL", "NM", "OTHER"].map((m) => <option key={m}>{m}</option>)}</Select></Field>
            <Field label="Body region"><Input value={f.bodyRegion} onChange={set("bodyRegion")} /></Field>
            <Field label="Price (KES)"><Input type="number" step="0.01" value={f.price} onChange={set("price")} /></Field>
          </Grid>
          <ErrorNote error={error} />
          <Button type="submit" busy={busy}>Add procedure</Button>
        </form>
      </Card>
    </Page>
  );
}

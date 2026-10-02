"use client";

import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { kes } from "@/lib/format";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, Table, Td, Tr, useAction } from "@/components/ui";

type Charge = { id: string; code: string; name: string; category: string; price: number; active: boolean };

export default function Charges() {
  const list = useFetch<Charge[]>("/v1/billing/charges?activeOnly=false");
  const [f, setF] = useState({ code: "", name: "", category: "CONSULTATION", price: "" });
  const { busy, error, run } = useAction();
  return (
    <Page title="Price list">
      <Card pad={false}><Table head={["Code", "Item", "Category", "Price"]} empty="No items yet.">{(list.data ?? []).map((c) => <Tr key={c.id}><Td>{c.code}</Td><Td>{c.name}</Td><Td>{c.category}</Td><Td>{kes(c.price)}</Td></Tr>)}</Table></Card>
      <Card title="Add an item">
        <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); void run(async () => { await post("/v1/billing/charges", { ...f, code: f.code.toUpperCase(), price: Number(f.price) }); setF({ ...f, code: "", name: "", price: "" }); await list.reload(); }); }}>
          <Grid cols={4}>
            <Field label="Code"><Input required value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} /></Field>
            <Field label="Name"><Input required value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
            <Field label="Category"><Select value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>{["CONSULTATION", "LAB", "PHARMACY", "IMAGING", "PROCEDURE", "BED", "OTHER"].map((c) => <option key={c}>{c}</option>)}</Select></Field>
            <Field label="Price (KES)"><Input type="number" step="0.01" required value={f.price} onChange={(e) => setF({ ...f, price: e.target.value })} /></Field>
          </Grid>
          <ErrorNote error={error} /><Button type="submit" busy={busy}>Add</Button>
        </form>
      </Card>
    </Page>
  );
}

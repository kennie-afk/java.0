"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { post } from "@/lib/api";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

export default function NewDrug() {
  const router = useRouter();
  const [f, setF] = useState({ genericName: "", strength: "", form: "Tablet", unit: "tablet", ppbCode: "", atcCode: "", controlled: "false", unitPrice: "", reorderLevel: "" });
  const { busy, error, run } = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Page title="Add product to the formulary">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { await post("/v1/pharmacy/drugs", { ...f, controlled: f.controlled === "true", unitPrice: f.unitPrice ? Number(f.unitPrice) : 0, reorderLevel: f.reorderLevel ? Number(f.reorderLevel) : 0, strength: f.strength || undefined, ppbCode: f.ppbCode || undefined, atcCode: f.atcCode || undefined }); router.push("/pharmacy"); }); }}>
        <Card><Grid>
          <Field label="Generic name"><Input required value={f.genericName} onChange={set("genericName")} /></Field>
          <Field label="Strength"><Input value={f.strength} onChange={set("strength")} /></Field>
          <Field label="Form"><Input required value={f.form} onChange={set("form")} /></Field>
          <Field label="Unit"><Input value={f.unit} onChange={set("unit")} /></Field>
          <Field label="PPB code" hint="Enter only if you have it"><Input value={f.ppbCode} onChange={set("ppbCode")} /></Field>
          <Field label="ATC code"><Input value={f.atcCode} onChange={set("atcCode")} /></Field>
          <Field label="Controlled drug"><Select value={f.controlled} onChange={set("controlled")}><option value="false">No</option><option value="true">Yes: needs a witness to dispense</option></Select></Field>
          <Field label="Unit price (KES)"><Input type="number" step="0.01" value={f.unitPrice} onChange={set("unitPrice")} /></Field>
          <Field label="Reorder level"><Input type="number" value={f.reorderLevel} onChange={set("reorderLevel")} /></Field>
        </Grid></Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy}>Save</Button>
      </form>
    </Page>
  );
}

"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { post, useFetch, type Slice } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

type Drug = { id: string; genericName: string; strength?: string; form: string };

export default function Receive() {
  const { facilityId } = useSession();
  const router = useRouter();
  const drugs = useFetch<Slice<Drug>>("/v1/pharmacy/drugs?limit=100");
  const [f, setF] = useState({ drugId: "", batchNo: "", expiryDate: "", quantity: "", unitCost: "", supplier: "" });
  const { busy, error, run } = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Page title="Receive stock">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { await post("/v1/pharmacy/stock/receipts", { facilityId, drugId: f.drugId || drugs.data?.data[0]?.id, batchNo: f.batchNo, expiryDate: f.expiryDate, quantity: Number(f.quantity), unitCost: f.unitCost ? Number(f.unitCost) : undefined, supplier: f.supplier || undefined }); router.push("/pharmacy"); }); }}>
        <Card><Grid>
          <Field label="Product"><Select value={f.drugId || drugs.data?.data[0]?.id || ""} onChange={set("drugId")}>{(drugs.data?.data ?? []).map((d) => <option key={d.id} value={d.id}>{d.genericName} {d.strength} {d.form}</option>)}</Select></Field>
          <Field label="Batch number"><Input required value={f.batchNo} onChange={set("batchNo")} /></Field>
          <Field label="Expiry date"><Input type="date" required value={f.expiryDate} onChange={set("expiryDate")} /></Field>
          <Field label="Quantity"><Input type="number" step="0.01" required value={f.quantity} onChange={set("quantity")} /></Field>
          <Field label="Unit cost (KES)"><Input type="number" step="0.01" value={f.unitCost} onChange={set("unitCost")} /></Field>
          <Field label="Supplier"><Input value={f.supplier} onChange={set("supplier")} /></Field>
        </Grid></Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy}>Receive</Button>
      </form>
    </Page>
  );
}

"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { post } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, Textarea, useAction } from "@/components/ui";

export default function NewWard() {
  const { facilityId } = useSession();
  const router = useRouter();
  const [f, setF] = useState({ name: "", kind: "GENERAL", beds: "" });
  const { busy, error, run } = useAction();
  return (
    <Page title="New ward">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { await post("/v1/inpatient/wards", { facilityId, name: f.name, kind: f.kind, bedLabels: f.beds.split(/[\s,]+/).filter(Boolean) }); router.push("/wards"); }); }}>
        <Card><Grid cols={2}>
          <Field label="Name"><Input required value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
          <Field label="Type"><Select value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value })}>{["GENERAL", "SURGICAL", "MEDICAL", "MATERNITY", "PAEDIATRIC", "NEWBORN", "ICU", "HDU", "ISOLATION", "PSYCHIATRIC", "OTHER"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
        </Grid>
        <div className="pt-3"><Field label="Bed labels" hint="Separate with spaces or commas, e.g. B1 B2 B3"><Textarea rows={2} required value={f.beds} onChange={(e) => setF({ ...f, beds: e.target.value })} /></Field></div></Card>
        <ErrorNote error={error} /><Button type="submit" busy={busy}>Create ward</Button>
      </form>
    </Page>
  );
}

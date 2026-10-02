"use client";

import { use, useState } from "react";
import { useRouter } from "next/navigation";
import { post } from "@/lib/api";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

export default function AddAllergy({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const [f, setF] = useState({ substance: "", category: "DRUG", severity: "MODERATE", reaction: "" });
  const { busy, error, run } = useAction();
  return (
    <Page title="Record allergy">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { await post(`/v1/clinical/patients/${id}/allergies`, f); router.push(`/patients/${id}`); }); }}>
        <Card>
          <Grid>
            <Field label="Substance" hint="Use the generic drug name so prescribing checks can match it"><Input required value={f.substance} onChange={(e) => setF({ ...f, substance: e.target.value })} /></Field>
            <Field label="Category"><Select value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}><option>DRUG</option><option>FOOD</option><option>ENVIRONMENT</option><option>OTHER</option></Select></Field>
            <Field label="Severity"><Select value={f.severity} onChange={(e) => setF({ ...f, severity: e.target.value })}><option>MILD</option><option>MODERATE</option><option>SEVERE</option><option>LIFE_THREATENING</option></Select></Field>
            <Field label="Reaction"><Input value={f.reaction} onChange={(e) => setF({ ...f, reaction: e.target.value })} /></Field>
          </Grid>
        </Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy}>Save</Button>
      </form>
    </Page>
  );
}

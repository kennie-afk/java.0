"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api, post, useFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

type Ward = { id: string; name: string };
type Bed = { id: string; label: string; status: string };

export default function Admit() {
  const { facilityId } = useSession();
  const router = useRouter();
  const sp = useSearchParams();
  const wards = useFetch<Ward[]>(`/v1/inpatient/wards?facilityId=${facilityId}`);
  const [wardId, setWardId] = useState("");
  const [bedId, setBedId] = useState(sp.get("bedId") ?? "");
  const [patient, setPatient] = useState<PatientRow | null>(null);
  const [dx, setDx] = useState("");
  const ward = wardId || wards.data?.[0]?.id || "";
  const beds = useFetch<Bed[]>(ward ? `/v1/inpatient/wards/${ward}/beds` : null);
  const free = (beds.data ?? []).filter((b) => b.status === "AVAILABLE");
  const preset = sp.get("bedId");
  useEffect(() => { if (preset) setBedId(preset); }, [preset]);
  const { busy, error, run } = useAction();
  void api;
  return (
    <Page title="Admit patient">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { const a = await post<{ id: string }>("/v1/inpatient/admissions", { facilityId, patientId: patient!.id, bedId: bedId || free[0]?.id, admittingDiagnosis: dx || undefined }); router.push(`/admissions/${a.id}`); }); }}>
        <Card><Grid cols={2}>
          <Field label="Patient"><PatientPicker value={patient} onPick={setPatient} /></Field>
          <Field label="Admitting diagnosis"><Input value={dx} onChange={(e) => setDx(e.target.value)} /></Field>
          <Field label="Ward"><Select value={ward} onChange={(e) => { setWardId(e.target.value); setBedId(""); }}>{(wards.data ?? []).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</Select></Field>
          <Field label="Bed"><Select value={bedId || free[0]?.id || ""} onChange={(e) => setBedId(e.target.value)}>{free.map((b) => <option key={b.id} value={b.id}>{b.label}</option>)}</Select></Field>
        </Grid>
        {free.length === 0 && <p className="pt-2 text-sm text-warn">No free beds in this ward.</p>}</Card>
        <ErrorNote error={error} /><Button type="submit" busy={busy} disabled={!patient || free.length === 0}>Admit</Button>
      </form>
    </Page>
  );
}

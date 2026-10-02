"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { api, post, useFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";
import { useEffect } from "react";

type Test = { id: string; code: string; name: string; price: number };

export default function NewLabOrder() {
  const { facilityId } = useSession();
  const sp = useSearchParams();
  const router = useRouter();
  const tests = useFetch<Test[]>("/v1/lab/tests");
  const [patient, setPatient] = useState<PatientRow | null>(null);
  const [picked, setPicked] = useState<string[]>([]);
  const [priority, setPriority] = useState("ROUTINE");
  const [info, setInfo] = useState("");
  const { busy, error, run } = useAction();
  const pid = sp.get("patientId");
  useEffect(() => {
    if (pid) api<PatientRow>(`/v1/patients/${pid}`).then(setPatient).catch(() => undefined);
  }, [pid]);
  return (
    <Page title="New laboratory order">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { const o = await post<{ id: string }>("/v1/lab/orders", { facilityId, patientId: patient!.id, encounterId: sp.get("encounterId") ?? undefined, priority, clinicalInfo: info || undefined, testIds: picked }); router.push(`/lab/${o.id}`); }); }}>
        <Card><Grid cols={3}>
          <Field label="Patient"><PatientPicker value={patient} onPick={setPatient} /></Field>
          <Field label="Priority"><Select value={priority} onChange={(e) => setPriority(e.target.value)}><option>ROUTINE</option><option>URGENT</option><option>STAT</option></Select></Field>
          <Field label="Clinical information"><Input value={info} onChange={(e) => setInfo(e.target.value)} /></Field>
        </Grid></Card>
        <Card title="Tests">
          <div className="grid grid-cols-1 gap-1 sm:grid-cols-3">
            {(tests.data ?? []).map((t) => (
              <label key={t.id} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={picked.includes(t.id)} onChange={(e) => setPicked(e.target.checked ? [...picked, t.id] : picked.filter((x) => x !== t.id))} /> {t.name} <span className="text-faint">{t.code}</span></label>
            ))}
          </div>
          {tests.data?.length === 0 && <p className="text-sm text-muted">The catalogue is empty. Add tests under Test catalogue.</p>}
        </Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy} disabled={!patient || picked.length === 0}>Place order</Button>
      </form>
    </Page>
  );
}

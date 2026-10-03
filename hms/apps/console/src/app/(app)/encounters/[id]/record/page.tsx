"use client";

import { use, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ApiError, post } from "@/lib/api";
import { postQueued } from "@/lib/offline";
import { Button, Card, ErrorNote, Field, Grid, Input, Notice, Page, Select, Textarea, useAction } from "@/components/ui";

const num = (s: string) => (s === "" ? undefined : Number(s));

export default function Record({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const kind = useSearchParams().get("kind") ?? "note";
  const thread = useSearchParams().get("thread");
  const router = useRouter();
  const [f, setF] = useState<Record<string, string>>({});
  const [override, setOverride] = useState<string | null>(null);
  const [queued, setQueued] = useState(false);
  const stampLabel = (what: string) => `${what}, ${new Date().toLocaleTimeString("en-KE", { hour: "2-digit", minute: "2-digit" })}`;
  const { busy, error, run } = useAction();
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value });
  const v = (k: string) => f[k] ?? "";
  const back = () => router.push(`/encounters/${id}`);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    void run(async () => {
      try {
        if (kind === "vitals") {
          const r = await postQueued(`/v1/clinical/encounters/${id}/vitals`, { tempC: num(v("tempC")), pulse: num(v("pulse")), respRate: num(v("respRate")), systolic: num(v("systolic")), diastolic: num(v("diastolic")), spo2: num(v("spo2")),
            weightKg: num(v("weightKg")), heightCm: num(v("heightCm")), muacCm: num(v("muacCm")), glucoseMmol: num(v("glucose")), painScore: num(v("painScore")) }, stampLabel("Vital signs"));
          if (r.queued) { setQueued(true); return; }
        } else if (kind === "triage") {
          await post(`/v1/clinical/encounters/${id}/triage`, { category: v("category") || "ROUTINE", chiefComplaint: v("complaint") });
        } else if (kind === "note") {
          const r = await postQueued(`/v1/clinical/encounters/${id}/notes`, { kind: v("noteKind") || "SOAP", body: v("body") }, stampLabel("Clinical note"));
          if (r.queued) { setQueued(true); return; }
        } else if (kind === "amend") {
          await post(`/v1/clinical/notes/${thread}/amend`, { body: v("body"), reason: v("reason") });
        } else if (kind === "diagnosis") {
          await post(`/v1/clinical/encounters/${id}/diagnoses`, { icd11Code: v("code").toUpperCase(), title: v("title"), kind: v("dxKind") || "SECONDARY", certainty: v("certainty") || "PROVISIONAL" });
        } else if (kind === "order") {
          const med = (v("orderKind") || "MEDICATION") === "MEDICATION";
          await post(`/v1/clinical/encounters/${id}/orders`, {
            kind: v("orderKind") || "MEDICATION", description: med ? `${v("drugName")} ${v("dose")}`.trim() : v("description"), priority: v("priority") || "ROUTINE",
            ...(med ? { drugName: v("drugName"), dose: v("dose") || undefined, route: v("route") || undefined, frequency: v("frequency") || undefined, durationDays: num(v("days")), quantity: num(v("quantity")), allergyOverrideReason: override ?? undefined } : {})
          });
        }
        back();
      } catch (err) {
        if (err instanceof ApiError && err.code === "allergy_conflict") setOverride("");
        throw err;
      }
    });
  };

  const titles: Record<string, string> = { vitals: "Record vitals", triage: "Triage", note: "Write note", amend: "Amend note", diagnosis: "Add diagnosis", order: "New order" };
  return (
    <Page title={titles[kind] ?? "Record"}>
      {queued && <Notice tone="warn" title="Saved on this device">There is no connection. This entry will be sent by itself when it returns; see Waiting to send. <a className="underline" href={`/encounters/${id}`}>Back to the visit</a></Notice>}
      <form onSubmit={submit} className="space-y-4">
        <Card>
          {kind === "vitals" && (
            <Grid cols={4}>
              <Field label="Temp C"><Input type="number" step="0.1" value={v("tempC")} onChange={set("tempC")} /></Field>
              <Field label="Pulse /min"><Input type="number" value={v("pulse")} onChange={set("pulse")} /></Field>
              <Field label="Resp rate /min"><Input type="number" value={v("respRate")} onChange={set("respRate")} /></Field>
              <Field label="SpO2 %"><Input type="number" value={v("spo2")} onChange={set("spo2")} /></Field>
              <Field label="Systolic"><Input type="number" value={v("systolic")} onChange={set("systolic")} /></Field>
              <Field label="Diastolic"><Input type="number" value={v("diastolic")} onChange={set("diastolic")} /></Field>
              <Field label="Weight kg"><Input type="number" step="0.1" value={v("weightKg")} onChange={set("weightKg")} /></Field>
              <Field label="Height cm"><Input type="number" step="0.1" value={v("heightCm")} onChange={set("heightCm")} /></Field>
              <Field label="MUAC cm"><Input type="number" step="0.1" value={v("muacCm")} onChange={set("muacCm")} /></Field>
              <Field label="Glucose mmol/L"><Input type="number" step="0.1" value={v("glucose")} onChange={set("glucose")} /></Field>
              <Field label="Pain 0-10"><Input type="number" min={0} max={10} value={v("painScore")} onChange={set("painScore")} /></Field>
            </Grid>
          )}
          {kind === "triage" && (
            <Grid cols={2}>
              <Field label="Category"><Select value={v("category") || "ROUTINE"} onChange={set("category")}><option>ROUTINE</option><option>PRIORITY</option><option>EMERGENCY</option></Select></Field>
              <Field label="Chief complaint"><Input required value={v("complaint")} onChange={set("complaint")} /></Field>
            </Grid>
          )}
          {kind === "note" && (
            <div className="space-y-3">
              <Field label="Type"><Select value={v("noteKind") || "SOAP"} onChange={set("noteKind")}>{["SOAP", "PROGRESS", "ADMISSION", "DISCHARGE", "PROCEDURE", "NURSING", "OTHER"].map((k) => <option key={k}>{k}</option>)}</Select></Field>
              <Field label="Note"><Textarea rows={8} required value={v("body")} onChange={set("body")} /></Field>
            </div>
          )}
          {kind === "amend" && (
            <div className="space-y-3">
              <p className="text-sm text-muted">The earlier text stays on record. State why it is being changed.</p>
              <Field label="New text"><Textarea rows={8} required value={v("body")} onChange={set("body")} /></Field>
              <Field label="Reason for amendment"><Input required minLength={5} value={v("reason")} onChange={set("reason")} /></Field>
            </div>
          )}
          {kind === "diagnosis" && (
            <Grid cols={2}>
              <Field label="ICD-11 code" hint="Shape is checked (e.g. 1A00, BA00.0); the code is not looked up in a catalogue"><Input required value={v("code")} onChange={set("code")} /></Field>
              <Field label="Diagnosis"><Input required value={v("title")} onChange={set("title")} /></Field>
              <Field label="Kind"><Select value={v("dxKind") || "SECONDARY"} onChange={set("dxKind")}><option>PRIMARY</option><option>SECONDARY</option></Select></Field>
              <Field label="Certainty"><Select value={v("certainty") || "PROVISIONAL"} onChange={set("certainty")}><option>PROVISIONAL</option><option>CONFIRMED</option><option>RULED_OUT</option></Select></Field>
            </Grid>
          )}
          {kind === "order" && (
            <div className="space-y-3">
              <Grid cols={3}>
                <Field label="Kind"><Select value={v("orderKind") || "MEDICATION"} onChange={set("orderKind")}><option>MEDICATION</option><option>PROCEDURE</option><option>IMAGING</option><option>REFERRAL</option><option>OTHER</option></Select></Field>
                <Field label="Priority"><Select value={v("priority") || "ROUTINE"} onChange={set("priority")}><option>ROUTINE</option><option>URGENT</option><option>STAT</option></Select></Field>
              </Grid>
              {(v("orderKind") || "MEDICATION") === "MEDICATION" ? (
                <Grid cols={3}>
                  <Field label="Drug (generic name)"><Input required value={v("drugName")} onChange={set("drugName")} /></Field>
                  <Field label="Dose"><Input value={v("dose")} onChange={set("dose")} /></Field>
                  <Field label="Route"><Input value={v("route")} onChange={set("route")} /></Field>
                  <Field label="Frequency"><Input value={v("frequency")} onChange={set("frequency")} /></Field>
                  <Field label="Days"><Input type="number" value={v("days")} onChange={set("days")} /></Field>
                  <Field label="Quantity to dispense"><Input type="number" required value={v("quantity")} onChange={set("quantity")} /></Field>
                </Grid>
              ) : (
                <Field label="Description"><Input required value={v("description")} onChange={set("description")} /></Field>
              )}
              {override !== null && (
                <div className="space-y-2 rounded-lg border border-[#f5cdcb] bg-danger-soft p-3">
                  <p className="text-sm text-danger">This drug matches a recorded allergy. To prescribe anyway, state why.</p>
                  <Input required minLength={10} placeholder="Reason (at least 10 characters)" value={override} onChange={(e) => setOverride(e.target.value)} />
                </div>
              )}
            </div>
          )}
        </Card>
        <ErrorNote error={error} />
        <div className="flex gap-2"><Button type="submit" busy={busy}>Save</Button><Button variant="secondary" onClick={back}>Cancel</Button></div>
      </form>
    </Page>
  );
}

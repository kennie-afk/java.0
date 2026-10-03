"use client";

import { useState } from "react";
import { papi, usePortal } from "@/lib/portal";
import { date, stamp, today, addDays } from "@/lib/format";
import { Badge, Button, Card, ErrorNote, Field, Input, Loading, Select, Status, Table, Td, Tabs, Tr, useAction } from "@/components/ui";

type Result = { id: string; test: string; valueNumeric?: number; valueText?: string; unit?: string; flag?: string; refLow?: number; refHigh?: number; releasedAt: string };
type Imaging = { id: string; procedure: string; modality: string; findings?: string; impression: string; releasedAt: string };
type Appt = { id: string; facility: string; startsAt: string; status: string; reason?: string };
type Req = { id: string; facility: string; preferredDate: string; reason: string; status: string; responseNote?: string };
type Med = { id: string; drug: string; dose?: string; route?: string; frequency?: string; durationDays?: number; status: string; prescribedAt: string };
type Allergy = { id: string; substance: string; reaction?: string; severity: string };

const FLAG: Record<string, string> = { N: "Normal", L: "Low", H: "High", LL: "Very low", HH: "Very high", A: "Abnormal" };

function Results() {
  const r = usePortal<{ data: Result[] }>("/results");
  if (r.loading) return <Loading />;
  return (
    <Card title="Test results" description="Your care team releases results here once they have been checked." pad={false}>
      <Table head={["Test", "Result", "Normal range", "Released"]} empty="No results have been released to you yet.">
        {(r.data?.data ?? []).map((x) => <Tr key={x.id}><Td>{x.test}</Td><Td>{x.valueNumeric ?? x.valueText} {x.unit} {x.flag && x.flag !== "N" && <Badge tone="warn">{FLAG[x.flag] ?? x.flag}</Badge>}</Td><Td>{x.refLow !== undefined || x.refHigh !== undefined ? `${x.refLow ?? ""} - ${x.refHigh ?? ""}` : ""}</Td><Td>{date(x.releasedAt)}</Td></Tr>)}
      </Table>
    </Card>
  );
}

function Scans() {
  const r = usePortal<Imaging[]>("/imaging");
  if (r.loading) return <Loading />;
  return (
    <div className="space-y-3">
      {(r.data ?? []).length === 0 && <Card><p className="text-sm text-muted">No scan reports have been released to you yet.</p></Card>}
      {(r.data ?? []).map((x) => (
        <Card key={x.id} title={`${x.procedure} (${x.modality})`} description={`Released ${stamp(x.releasedAt)}`}>
          {x.findings && <p className="whitespace-pre-wrap text-sm">{x.findings}</p>}
          <p className="pt-2 text-sm font-semibold">Summary: <span className="font-normal">{x.impression}</span></p>
        </Card>
      ))}
    </div>
  );
}

function Appointments() {
  const appts = usePortal<Appt[]>("/appointments");
  const reqs = usePortal<Req[]>("/appointment-requests");
  const facilities = usePortal<{ id: string; name: string }[]>("/facilities");
  const [f, setF] = useState({ facilityId: "", preferredDate: addDays(today(), 3), reason: "" });
  const act = useAction();
  return (
    <div className="space-y-4">
      <Card title="Your appointments" pad={false}>
        <Table head={["When", "Where", "Reason", "Status"]} empty="No appointments.">
          {(appts.data ?? []).map((a) => <Tr key={a.id}><Td>{stamp(a.startsAt)}</Td><Td>{a.facility}</Td><Td>{a.reason}</Td><Td><Status value={a.status} /></Td></Tr>)}
        </Table>
      </Card>
      <Card title="Ask for an appointment" description="The facility will answer here. This is a request, not a booking.">
        <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); void act.run(async () => { await papi("/appointment-requests", { method: "POST", body: { facilityId: f.facilityId || facilities.data?.[0]?.id, preferredDate: f.preferredDate, reason: f.reason } }); setF({ ...f, reason: "" }); await reqs.reload(); }); }}>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Field label="Where"><Select value={f.facilityId} onChange={(e) => setF({ ...f, facilityId: e.target.value })}>{(facilities.data ?? []).map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}</Select></Field>
            <Field label="Preferred date"><Input type="date" required value={f.preferredDate} min={today()} onChange={(e) => setF({ ...f, preferredDate: e.target.value })} /></Field>
            <Field label="Reason"><Input required minLength={3} value={f.reason} onChange={(e) => setF({ ...f, reason: e.target.value })} /></Field>
          </div>
          <ErrorNote error={act.error} />
          <Button type="submit" busy={act.busy}>Send request</Button>
        </form>
      </Card>
      <Card title="Your requests" pad={false}>
        <Table head={["Wanted", "Where", "Reason", "Status", ""]} empty="No requests yet.">
          {(reqs.data ?? []).map((r) => (
            <Tr key={r.id}><Td>{r.preferredDate}</Td><Td>{r.facility}</Td><Td>{r.reason}</Td><Td><Status value={r.status} /> {r.responseNote && <span className="text-xs text-muted">{r.responseNote}</span>}</Td>
              <Td>{r.status === "REQUESTED" && <Button variant="secondary" busy={act.busy} onClick={() => void act.run(async () => { await papi(`/appointment-requests/${r.id}/cancel`, { method: "POST", body: {} }); await reqs.reload(); })}>Cancel</Button>}</Td></Tr>
          ))}
        </Table>
      </Card>
    </div>
  );
}

function Medicines() {
  const m = usePortal<Med[]>("/medications");
  const a = usePortal<Allergy[]>("/allergies");
  return (
    <div className="space-y-4">
      <Card title="Medicines prescribed in the last six months" pad={false}>
        <Table head={["Medicine", "How to take it", "Prescribed"]} empty="Nothing prescribed recently.">
          {(m.data ?? []).map((x) => <Tr key={x.id}><Td>{x.drug}</Td><Td>{[x.dose, x.route, x.frequency, x.durationDays ? `for ${x.durationDays} days` : ""].filter(Boolean).join(", ")}</Td><Td>{stamp(x.prescribedAt)}</Td></Tr>)}
        </Table>
      </Card>
      <Card title="Allergies on your record" pad={false}>
        <Table head={["Allergy", "Reaction", "Severity"]} empty="None recorded.">
          {(a.data ?? []).map((x) => <Tr key={x.id}><Td>{x.substance}</Td><Td>{x.reaction}</Td><Td><Status value={x.severity} /></Td></Tr>)}
        </Table>
      </Card>
    </div>
  );
}

export default function PortalHome() {
  const [tab, setTab] = useState("results");
  return (
    <>
      <Tabs tabs={[{ key: "results", label: "Results" }, { key: "scans", label: "Scans" }, { key: "appointments", label: "Appointments" }, { key: "medicines", label: "Medicines" }]} value={tab} onChange={setTab} />
      {tab === "results" && <Results />}
      {tab === "scans" && <Scans />}
      {tab === "appointments" && <Appointments />}
      {tab === "medicines" && <Medicines />}
      <p className="text-xs text-muted">If a result worries you, speak to your clinician. In an emergency, go to the nearest facility; do not wait for this page.</p>
    </>
  );
}

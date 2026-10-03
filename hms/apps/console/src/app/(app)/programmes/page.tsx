"use client";

import { useState } from "react";
import { useFetch, usePaged } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, Loading, More, Page, Select, Stat, Status, Table, Td, Tr, Tabs } from "@/components/ui";

type Row = { id: string; patientName: string; programme: string; registerNo: string; enrolledOn: string; status: string; nextVisitOn?: string; daysOverdue?: number };
type Defaulter = { id: string; patientName: string; phone?: string; programme: string; registerNo: string; nextVisitOn: string; daysOverdue: number };
type Summary = { graceDays: number; programmes: { programme: string; active: number; missedVisit: number; outcomesInPeriod: number }[] };

const PROGRAMMES = ["HIV", "TB", "HYPERTENSION", "DIABETES", "ASTHMA", "EPILEPSY"];

export default function Programmes() {
  const { facilityId, can } = useSession();
  const [tab, setTab] = useState("register");
  const [programme, setProgramme] = useState("");
  const [status, setStatus] = useState("ACTIVE");
  const list = usePaged<Row>(`/v1/programmes/enrolments?facilityId=${facilityId}${programme ? `&programme=${programme}` : ""}${status ? `&status=${status}` : ""}`);
  const sum = useFetch<Summary>(`/v1/programmes/summary?facilityId=${facilityId}`);
  const def = useFetch<Defaulter[]>(tab === "defaulters" ? `/v1/programmes/defaulters?facilityId=${facilityId}${programme ? `&programme=${programme}` : ""}` : null);
  const total = (k: "active" | "missedVisit") => (sum.data?.programmes ?? []).reduce((a, p) => a + p[k], 0);
  return (
    <Page title="Programmes" sub="Follow-up registers kept locally; not the Ministry of Health's official registers." actions={can("programmes:write") ? <Button href="/programmes/new">Enrol patient</Button> : undefined}>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Active enrolments" value={total("active")} />
        <Stat label={`Missed visit (${sum.data?.graceDays ?? 7}+ days)`} value={total("missedVisit")} tone={total("missedVisit") > 0 ? "danger" : undefined} />
        {(sum.data?.programmes ?? []).slice(0, 2).map((p) => <Stat key={p.programme} label={p.programme} value={p.active} hint={`${p.missedVisit} overdue`} />)}
      </div>
      <Tabs tabs={[{ key: "register", label: "Register" }, { key: "defaulters", label: "To trace" }]} value={tab} onChange={setTab} />
      <Card pad={false}>
        <div className="flex gap-2 border-b border-line p-3">
          <Select value={programme} onChange={(e) => setProgramme(e.target.value)} className="max-w-48"><option value="">All programmes</option>{PROGRAMMES.map((p) => <option key={p}>{p}</option>)}</Select>
          {tab === "register" && <Select value={status} onChange={(e) => setStatus(e.target.value)} className="max-w-48"><option value="">All statuses</option>{["ACTIVE", "TRANSFERRED_OUT", "LOST_TO_FOLLOW_UP", "COMPLETED", "DIED", "STOPPED"].map((s) => <option key={s}>{s}</option>)}</Select>}
        </div>
        {tab === "register" ? (list.loading && list.items.length === 0 ? <Loading /> : (
          <Table head={["Register no", "Patient", "Programme", "Enrolled", "Status", "Next visit"]} empty="No enrolments.">
            {list.items.map((r) => <Tr key={r.id} tone={r.daysOverdue ? "danger" : undefined}><Td href={`/programmes/${r.id}`}>{r.registerNo}</Td><Td>{r.patientName}</Td><Td>{r.programme}</Td><Td>{r.enrolledOn}</Td><Td><Status value={r.status} /></Td><Td>{r.nextVisitOn ?? ""}{r.daysOverdue ? ` (${r.daysOverdue} days late)` : ""}</Td></Tr>)}
          </Table>
        )) : (def.loading ? <Loading /> : (
          <Table head={["Register no", "Patient", "Phone", "Programme", "Was due", "Days late"]} empty="Nobody is overdue past the grace period.">
            {(def.data ?? []).map((d) => <Tr key={d.id}><Td href={`/programmes/${d.id}`}>{d.registerNo}</Td><Td>{d.patientName}</Td><Td>{d.phone}</Td><Td>{d.programme}</Td><Td>{d.nextVisitOn}</Td><Td>{d.daysOverdue}</Td></Tr>)}
          </Table>
        ))}
        {tab === "register" && <More onMore={list.more} loading={list.loading} />}
      </Card>
    </Page>
  );
}

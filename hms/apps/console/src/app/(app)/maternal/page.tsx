"use client";

import { useState } from "react";
import { useFetch, usePaged } from "@/lib/api";
import { date } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, Grid, More, Page, Select, Stat, Status, Table, Td, Tr } from "@/components/ui";

type Preg = { id: string; patientName: string; edd: string; gestationWeeks: number; gestationDays: number; status: string; visitCount: number; nextVisitOn?: string; overdue: boolean; flags: { level: string }[] };

type Summary = { from: string; to: string; pregnancies: { active: number; overdueForVisit: number; latestVisitHasDangerSign: number }; antenatal: { visits: number; firstVisits: number };
  deliveries: { total: number; liveBirths: number; lowBirthWeight: number }; immunisation: { dosesGiven: number } };

export default function Maternal() {
  const { facilityId, can } = useSession();
  const [status, setStatus] = useState("ACTIVE");
  const [overdue, setOverdue] = useState("");
  const sum = useFetch<Summary>(`/v1/mch/summary?facilityId=${facilityId}`);
  const list = usePaged<Preg>(`/v1/mch/pregnancies?facilityId=${facilityId}&status=${status}${overdue ? "&overdue=true" : ""}`);
  return (
    <Page title="Antenatal and maternity" actions={<>
      <Button variant="secondary" href="/immunisation">Immunisation</Button>
      {can("mch:write") && <Button href="/maternal/new">Open pregnancy</Button>}
    </>}>
      {sum.data && (
        <Grid cols={4}>
          <Stat label="Ongoing pregnancies" value={sum.data.pregnancies.active} hint={`${sum.data.pregnancies.overdueForVisit} overdue for a visit`} tone={sum.data.pregnancies.overdueForVisit ? "warn" : undefined} />
          <Stat label="Danger sign at last visit" value={sum.data.pregnancies.latestVisitHasDangerSign} tone={sum.data.pregnancies.latestVisitHasDangerSign ? "danger" : undefined} />
          <Stat label="Antenatal visits, 30 days" value={sum.data.antenatal.visits} hint={`${sum.data.antenatal.firstVisits} first visits`} />
          <Stat label="Deliveries, 30 days" value={sum.data.deliveries.total} hint={`${sum.data.deliveries.liveBirths} live births, ${sum.data.deliveries.lowBirthWeight} under 2.5 kg · ${sum.data.immunisation.dosesGiven} vaccine doses`} />
        </Grid>
      )}
      <div className="flex gap-2">
        <Select value={status} onChange={(e) => setStatus(e.target.value)} className="max-w-40">{["ACTIVE", "DELIVERED", "LOST"].map((s) => <option key={s}>{s}</option>)}</Select>
        <Select value={overdue} onChange={(e) => setOverdue(e.target.value)} className="max-w-48"><option value="">All</option><option value="1">Overdue for a visit</option></Select>
      </div>
      <Card pad={false}>
        <Table head={["Mother", "Gestation", "EDD", "Visits", "Next visit", "Flags", "Status"]} empty="No pregnancies match.">
          {list.items.map((p) => (
            <Tr key={p.id} tone={p.flags.some((f) => f.level === "DANGER") ? "danger" : p.overdue ? "warn" : undefined}>
              <Td href={`/maternal/${p.id}`}>{p.patientName}</Td>
              <Td>{p.gestationWeeks}w {p.gestationDays}d</Td><Td>{date(p.edd)}</Td><Td>{p.visitCount}</Td>
              <Td>{date(p.nextVisitOn)}{p.overdue && <> <Badge tone="warn">Overdue</Badge></>}</Td>
              <Td>{p.flags.length || ""}</Td><Td><Status value={p.status} /></Td>
            </Tr>
          ))}
        </Table>
        <More onMore={list.more} loading={list.loading} />
      </Card>
    </Page>
  );
}

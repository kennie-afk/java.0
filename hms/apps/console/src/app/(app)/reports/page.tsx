"use client";

import { useState } from "react";
import { useFetch } from "@/lib/api";
import { addDays, kes, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Card, ErrorNote, Field, Grid, Input, Loading, Page, Stat, Table, Td, Tr } from "@/components/ui";

type Count = { key: string; count: number };
type Money = { key: string; amount: number; count: number };
type Overview = {
  outpatient: { visits: number; uniquePatients: number; under5: number; fiveAndOver: number; bySex: Count[]; byAgeBand: Count[]; topDiagnoses: Count[]; note: string };
  finance: { invoiced: number; collected: number; reversed: number; outstanding: number; collectedByMethod: Money[]; invoicedByPayer: Money[] };
  inpatient: { admissions: number; discharges: number; averageStayDays?: number; dischargesByOutcome: Count[]; bedsTotal: number; bedsOccupied: number; occupancyPercent: number };
  laboratory: { orders: number; tests: number; validated: number; averageTurnaroundMinutes?: number; byFlag: Count[]; unacknowledgedCritical: number };
};

const List = ({ rows }: { rows: Count[] }) => (
  <Table head={["", "Count"]} empty="None.">{rows.map((r) => <Tr key={r.key}><Td>{r.key}</Td><Td className="tabular-nums">{r.count}</Td></Tr>)}</Table>
);

export default function Reports() {
  const { facilityId } = useSession();
  const [from, setFrom] = useState(addDays(today(), -29));
  const [to, setTo] = useState(today());
  const r = useFetch<Overview>(`/v1/reports/overview?facilityId=${facilityId}&from=${from}&to=${to}`);
  return (
    <Page title="Reports" sub="Counts and totals for this facility. No patient is named. These are operational reports, not the official MOH returns.">
      <Grid cols={4}><Field label="From"><Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field><Field label="To"><Input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field></Grid>
      <ErrorNote error={r.error} />
      {r.loading && !r.data ? <Loading /> : r.data && (
        <>
          <Grid cols={4}>
            <Stat label="Outpatient visits" value={r.data.outpatient.visits} /><Stat label="People seen" value={r.data.outpatient.uniquePatients} />
            <Stat label="Under 5" value={r.data.outpatient.under5} /><Stat label="5 and over" value={r.data.outpatient.fiveAndOver} />
            <Stat label="Invoiced" value={kes(r.data.finance.invoiced)} /><Stat label="Collected" value={kes(r.data.finance.collected)} />
            <Stat label="Reversed" value={kes(r.data.finance.reversed)} /><Stat label="Outstanding" value={kes(r.data.finance.outstanding)} />
            <Stat label="Admissions" value={r.data.inpatient.admissions} /><Stat label="Discharges" value={r.data.inpatient.discharges} />
            <Stat label="Average stay (days)" value={r.data.inpatient.averageStayDays ?? "-"} /><Stat label="Bed occupancy" value={`${r.data.inpatient.occupancyPercent}%`} />
            <Stat label="Lab tests" value={r.data.laboratory.tests} /><Stat label="Validated" value={r.data.laboratory.validated} />
            <Stat label="Average turnaround (min)" value={r.data.laboratory.averageTurnaroundMinutes ?? "-"} /><Stat label="Unacknowledged critical" value={r.data.laboratory.unacknowledgedCritical} tone={r.data.laboratory.unacknowledgedCritical ? "danger" : undefined} />
          </Grid>
          <Grid cols={3}>
            <Card title="Visits by sex" pad={false}><List rows={r.data.outpatient.bySex} /></Card>
            <Card title="Visits by age" pad={false}><List rows={r.data.outpatient.byAgeBand} /></Card>
            <Card title="Top diagnoses" pad={false}><List rows={r.data.outpatient.topDiagnoses} /></Card>
            <Card title="Collected by method" pad={false}><Table head={["Method", "Amount"]} empty="None.">{r.data.finance.collectedByMethod.map((m) => <Tr key={m.key}><Td>{m.key}</Td><Td>{kes(m.amount)}</Td></Tr>)}</Table></Card>
            <Card title="Discharge outcomes" pad={false}><List rows={r.data.inpatient.dischargesByOutcome} /></Card>
            <Card title="Lab result flags" pad={false}><List rows={r.data.laboratory.byFlag} /></Card>
          </Grid>
          <p className="text-sm text-muted">{r.data.outpatient.note}</p>
        </>
      )}
    </Page>
  );
}

"use client";

import { useState } from "react";
import { useFetch } from "@/lib/api";
import { addDays, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Card, ErrorNote, Field, Grid, Input, Loading, Page, Stat, Table, Td, Tr } from "@/components/ui";

type Lab = { orders: number; tests: number; validated: number; averageTurnaroundMinutes?: number; byFlag: { key: string; count: number }[]; unacknowledgedCritical: number };

export default function LabReport() {
  const { facilityId } = useSession();
  const [from, setFrom] = useState(addDays(today(), -29));
  const [to, setTo] = useState(today());
  const r = useFetch<Lab>(`/v1/reports/laboratory?facilityId=${facilityId}&from=${from}&to=${to}`);
  return (
    <Page title="Laboratory report" sub="Counts for this facility. No patient is named.">
      <Grid cols={4}><Field label="From"><Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field><Field label="To"><Input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field></Grid>
      <ErrorNote error={r.error} />
      {r.loading && !r.data ? <Loading /> : r.data && (
        <>
          <Grid cols={4}>
            <Stat label="Orders" value={r.data.orders} /><Stat label="Tests" value={r.data.tests} /><Stat label="Validated" value={r.data.validated} />
            <Stat label="Average turnaround (min)" value={r.data.averageTurnaroundMinutes ?? "-"} />
          </Grid>
          <Stat label="Unacknowledged critical" value={r.data.unacknowledgedCritical} tone={r.data.unacknowledgedCritical ? "danger" : undefined} />
          <Card title="Result flags" pad={false}><Table head={["Flag", "Count"]} empty="None.">{r.data.byFlag.map((f) => <Tr key={f.key}><Td>{f.key}</Td><Td className="tabular-nums">{f.count}</Td></Tr>)}</Table></Card>
        </>
      )}
    </Page>
  );
}

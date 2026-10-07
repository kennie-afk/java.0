"use client";

import { useState } from "react";
import { useFetch } from "@/lib/api";
import { addDays, kes, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Card, ErrorNote, Field, Grid, Input, Loading, Page, Stat, Table, Td, Tr } from "@/components/ui";

type Money = { key: string; amount: number; count: number };
type Finance = { invoiced: number; collected: number; reversed: number; outstanding: number; invoices: number; collectedByMethod: Money[]; invoicedByPayer: Money[] };

const Lines = ({ rows }: { rows: Money[] }) => (
  <Table head={["", "Amount", "Count"]} empty="None.">{rows.map((m) => <Tr key={m.key}><Td>{m.key}</Td><Td className="tabular-nums">{kes(m.amount)}</Td><Td className="tabular-nums">{m.count}</Td></Tr>)}</Table>
);

export default function FinanceReport() {
  const { facilityId } = useSession();
  const [from, setFrom] = useState(addDays(today(), -29));
  const [to, setTo] = useState(today());
  const r = useFetch<Finance>(`/v1/reports/finance?facilityId=${facilityId}&from=${from}&to=${to}`);
  return (
    <Page title="Finance report" sub="Totals for this facility. No patient is named. Operational, not an accounting statement." actions={undefined}>
      <Grid cols={4}><Field label="From"><Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field><Field label="To"><Input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field></Grid>
      <ErrorNote error={r.error} />
      {r.loading && !r.data ? <Loading /> : r.data && (
        <>
          <Grid cols={4}><Stat label="Invoiced" value={kes(r.data.invoiced)} /><Stat label="Collected" value={kes(r.data.collected)} /><Stat label="Reversed" value={kes(r.data.reversed)} /><Stat label="Outstanding" value={kes(r.data.outstanding)} /></Grid>
          <Grid cols={2}><Card title="Collected by method" pad={false}><Lines rows={r.data.collectedByMethod} /></Card><Card title={`Invoiced by payer (${r.data.invoices} invoices)`} pad={false}><Lines rows={r.data.invoicedByPayer} /></Card></Grid>
        </>
      )}
    </Page>
  );
}

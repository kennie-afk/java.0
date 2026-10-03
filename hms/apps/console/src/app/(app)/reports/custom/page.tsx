"use client";

import { useState } from "react";
import { api, useFetch } from "@/lib/api";
import { addDays, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Field, Grid, Input, Loading, Notice, Page, Select, Table, Td, Tr, useAction } from "@/components/ui";

type Definition = { id: string; code: string; name: string; description?: string; elements: { code: string; label: string; measure: string; disaggregation?: string; filter?: string }[]; dhis2DataSet?: string };
type Cell = { element: string; label: string; category: string; value: number };
type Run = { name: string; from: string; to: string; cells: Cell[]; note: string };

async function download(path: string, name: string, type: string) {
  const res = await fetch(`/api${path}`);
  if (!res.ok) throw new Error((await res.json().catch(() => ({}))).detail ?? "The export failed.");
  const url = URL.createObjectURL(new Blob([await res.blob()], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

export default function CustomReports() {
  const { facilityId, can } = useSession();
  const defs = useFetch<Definition[]>("/v1/report-definitions");
  const [id, setId] = useState("");
  const [from, setFrom] = useState(addDays(today(), -29));
  const [to, setTo] = useState(today());
  const [run, setRun] = useState<Run | null>(null);
  const act = useAction();
  const q = `facilityId=${facilityId}&from=${from}&to=${to}`;
  const chosen = defs.data?.find((d) => d.id === id);
  return (
    <Page title="Custom reports" sub="The organisation's own counts, chosen from the system's measures. Not an official Ministry of Health return." actions={can("reports:manage") ? <Button href="/reports/custom/new">New report</Button> : undefined}>
      {defs.loading ? <Loading /> : (
        <Card>
          <Grid cols={4}>
            <Field label="Report"><Select value={id} onChange={(e) => { setId(e.target.value); setRun(null); }}><option value="">Choose</option>{(defs.data ?? []).map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</Select></Field>
            <Field label="From"><Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
            <Field label="To"><Input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
          </Grid>
          <div className="flex flex-wrap gap-2 pt-3">
            <Button busy={act.busy} disabled={!id} onClick={() => void act.run(async () => setRun(await api<Run>(`/v1/report-definitions/${id}/run?${q}`)))}>Run</Button>
            <Button variant="secondary" disabled={!id} onClick={() => void act.run(() => download(`/v1/report-definitions/${id}/export.csv?${q}`, `${chosen?.code ?? "report"}-${from}-${to}.csv`, "text/csv"))}>Download CSV</Button>
            {chosen?.dhis2DataSet && <Button variant="secondary" onClick={() => void act.run(() => download(`/v1/report-definitions/${id}/export.dhis2?${q}`, `${chosen.code}-dhis2-${from}.json`, "application/json"))}>DHIS2 file</Button>}
          </div>
          <ErrorNote error={act.error} />
        </Card>
      )}
      {defs.data?.length === 0 && <Notice title="No custom reports yet">{can("reports:manage") ? "Create one from New report." : "Ask an administrator to define one."}</Notice>}
      {run && (
        <Card title={run.name} description={`${run.from} to ${run.to}`} pad={false}>
          <Table head={["Element", "Category", "Count"]} empty="No data.">
            {run.cells.map((c, i) => <Tr key={i}><Td>{c.label}</Td><Td>{c.category}</Td><Td className="tabular-nums">{c.value}</Td></Tr>)}
          </Table>
          <p className="p-3 text-xs text-muted">{run.note}</p>
        </Card>
      )}
    </Page>
  );
}

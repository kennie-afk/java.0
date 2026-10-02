"use client";

import { use } from "react";
import { post, useFetch } from "@/lib/api";
import { kes, stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, Confirm, ErrorNote, KV, Grid, Loading, Page, Status, Table, Td, Tr, useAction, Notice } from "@/components/ui";

type Issue = { ruleCode: string; severity: string; field?: string; message: string };
type Sub = { id: string; adapter: string; verified: boolean; sent: boolean; outcome: string; detail?: string; submittedAt: string };
type Claim = { id: string; claimNumber: string; patientId: string; patientName: string; invoiceId: string; status: string; total: number; assembledAt: string; issues: Issue[]; submissions: Sub[]; disclaimer: string; bundle: { diagnoses: { code: string; title: string; kind: string }[]; lines: { description: string; total: number }[] } };

export default function ClaimPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const c = useFetch<Claim>(`/v1/claims/${id}`);
  const act = useAction();
  const go = (fn: () => Promise<unknown>) => act.run(async () => { await fn(); await c.reload(); });
  if (c.loading || !c.data) return <Page title="Claim">{c.error ? <ErrorNote error={c.error} /> : <Loading />}</Page>;
  const d = c.data;
  const open = ["NEEDS_ATTENTION", "READY", "DRAFT"].includes(d.status);
  return (
    <Page title={d.claimNumber} sub={`${d.patientName} · ${kes(d.total)} · assembled ${stamp(d.assembledAt)}`} actions={<>
      <Status value={d.status} />
      {can("claims:submit") && open && <Button variant="secondary" busy={act.busy} onClick={() => void go(() => post(`/v1/claims/${id}/reassemble`))}>Re-check against the record</Button>}
      {can("claims:submit") && d.status === "READY" && <Button busy={act.busy} onClick={() => void go(() => post(`/v1/claims/${id}/submit`))}>Submit (stub: sends nothing)</Button>}
      {can("claims:submit") && open && <Confirm label="Withdraw" prompt="Withdraw this claim?" needsReason onConfirm={(reason) => go(() => post(`/v1/claims/${id}/withdraw`, { reason }))} />}
    </>}>
      <Notice tone="warn" title="Unverified">{d.disclaimer}</Notice>
      <ErrorNote error={act.error} />
      <Card title="Readiness" pad={false}>
        <Table head={["Severity", "Rule", "Field", "Detail"]} empty="No issues. This claim passes every readiness check.">
          {d.issues.map((i, n) => <Tr key={n}><Td><Badge tone={i.severity === "ERROR" ? "danger" : "warn"}>{i.severity}</Badge></Td><Td>{i.ruleCode}</Td><Td>{i.field}</Td><Td>{i.message}</Td></Tr>)}
        </Table>
      </Card>
      <Grid cols={2}>
        <Card title="Diagnoses in the claim" pad={false}><Table head={["Code", "Diagnosis", "Kind"]} empty="None.">{d.bundle.diagnoses.map((x) => <Tr key={x.code}><Td>{x.code}</Td><Td>{x.title}</Td><Td>{x.kind}</Td></Tr>)}</Table></Card>
        <Card title="Charges in the claim" pad={false}><Table head={["Item", "Total"]} empty="None.">{d.bundle.lines.map((x, n) => <Tr key={n}><Td>{x.description}</Td><Td>{kes(x.total)}</Td></Tr>)}</Table></Card>
      </Grid>
      <Card title="Submission attempts" pad={false}>
        <Table head={["When", "Adapter", "Verified", "Sent", "Outcome"]} empty="Not submitted.">
          {d.submissions.map((s) => <Tr key={s.id}><Td>{stamp(s.submittedAt)}</Td><Td>{s.adapter}</Td><Td>{s.verified ? "yes" : "no"}</Td><Td>{s.sent ? "yes" : "no"}</Td><Td>{s.outcome}: {s.detail}</Td></Tr>)}
        </Table>
      </Card>
      <KV k="Invoice" v={<a className="text-accent hover:underline" href={`/billing/${d.invoiceId}`}>Open invoice</a>} />
    </Page>
  );
}

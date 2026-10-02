"use client";

import { useState } from "react";
import { useFetch, usePaged } from "@/lib/api";
import { kes, stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Card, Grid, Loading, More, Page, Select, Stat, Status, Table, Td, Tr, Notice } from "@/components/ui";

type Row = { id: string; claimNumber: string; patientName: string; status: string; total: number; errors: number; warnings: number; assembledAt: string };
type Summary = { byStatus: Record<string, number>; topIssues: { ruleCode: string; severity: string; claims: number }[] };

export default function Claims() {
  const { facilityId } = useSession();
  const [status, setStatus] = useState("");
  const summary = useFetch<Summary>(`/v1/claims/summary?facilityId=${facilityId}`);
  const list = usePaged<Row>(`/v1/claims?facilityId=${facilityId}${status ? `&status=${status}` : ""}`);
  return (
    <Page title="Claims readiness (Madai)" sub="Checks a claim is complete and consistent before it goes to a payer. Nothing is transmitted to SHA or DHA.">
      <Notice tone="warn" title="Unverified">
        The rules here are HMS&apos;s own, not the SHA/DHA rule set, and no live connection exists. Submitting only records the attempt.
      </Notice>
      {summary.data && (
        <Grid cols={4}>
          {["READY", "NEEDS_ATTENTION", "SUBMISSION_STUBBED", "WITHDRAWN"].map((s) => <Stat key={s} label={s.replaceAll("_", " ")} value={summary.data!.byStatus[s] ?? 0} tone={s === "NEEDS_ATTENTION" && (summary.data!.byStatus[s] ?? 0) > 0 ? "warn" : undefined} />)}
        </Grid>
      )}
      {summary.data && summary.data.topIssues.length > 0 && (
        <Card title="What is holding claims back">
          <ul className="divide-y divide-line text-base">{summary.data.topIssues.map((i) => <li key={i.ruleCode + i.severity} className="flex items-center justify-between py-2.5"><span className="flex items-center gap-3">{i.ruleCode.replaceAll("_", " ")} <Badge tone={i.severity === "ERROR" ? "danger" : "warn"}>{i.severity}</Badge></span><b>{i.claims}</b></li>)}</ul>
        </Card>
      )}
      <Card pad={false}>
        <div className="border-b border-line p-3"><Select value={status} onChange={(e) => setStatus(e.target.value)} className="max-w-56"><option value="">All statuses</option>{["DRAFT", "NEEDS_ATTENTION", "READY", "SUBMISSION_STUBBED", "WITHDRAWN"].map((s) => <option key={s}>{s}</option>)}</Select></div>
        {list.loading && list.items.length === 0 ? <Loading /> : (
          <Table head={["Claim", "Patient", "Status", "Total", "Errors", "Warnings", "Assembled"]} empty="No claims. Prepare one from an issued invoice.">
            {list.items.map((c) => <Tr key={c.id}><Td href={`/claims/${c.id}`}>{c.claimNumber}</Td><Td>{c.patientName}</Td><Td><Status value={c.status} /></Td><Td>{kes(c.total)}</Td><Td>{c.errors}</Td><Td>{c.warnings}</Td><Td>{stamp(c.assembledAt)}</Td></Tr>)}
          </Table>
        )}
        <More onMore={list.more} loading={list.loading} />
      </Card>
    </Page>
  );
}

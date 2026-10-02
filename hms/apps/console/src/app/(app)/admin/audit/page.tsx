"use client";

import { useState } from "react";
import { useFetch } from "@/lib/api";
import { stamp } from "@/lib/format";
import { Badge, Button, Card, ErrorNote, Field, Grid, Input, Page, Select, Table, Td, Tr, useAction } from "@/components/ui";
import { api } from "@/lib/api";

type Verification = { chainKey: string; intact: boolean; checked: number; brokenAtSeq?: number };
type Event = { seq: number; at: string; actorId?: string; action: string; reason?: string; detail: string };

export default function Audit() {
  const verify = useFetch<Verification[]>("/v1/audit/verify");
  const [q, setQ] = useState({ entityType: "patient", entityId: "" });
  const [events, setEvents] = useState<Event[] | null>(null);
  const act = useAction();
  return (
    <Page title="Audit" sub="Every change and every chart opened is recorded in a hash chain. Verification re-walks it.">
      <Card title="Chain verification" actions={<Button variant="secondary" onClick={() => void verify.reload()}>Re-verify</Button>} pad={false}>
        <Table head={["Chain", "Events checked", "Result"]} empty="No events yet.">
          {(verify.data ?? []).map((v) => <Tr key={v.chainKey}><Td>{v.chainKey}</Td><Td>{v.checked}</Td><Td>{v.intact ? <Badge tone="good">Intact</Badge> : <Badge tone="danger">Broken at {v.brokenAtSeq}</Badge>}</Td></Tr>)}
        </Table>
      </Card>
      <Card title="Look up a record's history">
        <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); void act.run(async () => setEvents(await api<Event[]>(`/v1/audit/events?entityType=${q.entityType}&entityId=${encodeURIComponent(q.entityId)}&limit=100`))); }}>
          <Grid cols={3}>
            <Field label="Record type"><Select value={q.entityType} onChange={(e) => setQ({ ...q, entityType: e.target.value })}>{["patient", "encounter", "invoice", "claim", "admission", "practitioner", "role", "facility", "drug"].map((t) => <option key={t}>{t}</option>)}</Select></Field>
            <Field label="Record id"><Input required value={q.entityId} onChange={(e) => setQ({ ...q, entityId: e.target.value })} /></Field>
          </Grid>
          <ErrorNote error={act.error} /><Button type="submit" busy={act.busy}>Look up</Button>
        </form>
      </Card>
      {events && (
        <Card pad={false}><Table head={["When", "Action", "Who", "Reason", "Detail"]} empty="No events for that record.">
          {events.map((e) => <Tr key={e.seq + e.at}><Td>{stamp(e.at)}</Td><Td>{e.action}</Td><Td>{e.actorId?.slice(0, 8)}</Td><Td>{e.reason}</Td><Td className="text-muted">{e.detail}</Td></Tr>)}
        </Table></Card>
      )}
    </Page>
  );
}

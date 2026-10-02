"use client";

import Link from "next/link";
import { useState } from "react";
import { useFetch, usePaged } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, Loading, More, Page, Select, Status, Table, Td, Tr, Notice } from "@/components/ui";

type Row = { id: string; orderNumber: string; patientName: string; priority: string; status: string; createdAt: string; items: number; hasUnacknowledgedCritical: boolean };
type Critical = { itemId: string; orderId: string; orderNumber: string; patientName: string; testName: string; resultNumeric?: number; unit?: string; flag: string };

export default function Lab() {
  const { facilityId, can } = useSession();
  const [status, setStatus] = useState("");
  const list = usePaged<Row>(`/v1/lab/orders?facilityId=${facilityId}${status ? `&status=${status}` : ""}`);
  const crit = useFetch<Critical[]>(`/v1/lab/critical?facilityId=${facilityId}`);
  return (
    <Page title="Laboratory" actions={<>
      {can("lab:manage") && <Button variant="secondary" href="/lab/tests">Test catalogue</Button>}
      {can("orders:write") && <Button href="/lab/new">New order</Button>}
    </>}>
      {crit.data && crit.data.length > 0 && (
        <Notice tone="danger" title="Critical results awaiting acknowledgement">
          <ul className="pt-1">{crit.data.map((c) => <li key={c.itemId}><Link className="underline" href={`/lab/${c.orderId}`}>{c.orderNumber}</Link> {c.patientName}: {c.testName} {c.resultNumeric} {c.unit} ({c.flag})</li>)}</ul>
        </Notice>
      )}
      <Card pad={false}>
        <div className="border-b border-line p-3"><Select value={status} onChange={(e) => setStatus(e.target.value)} className="max-w-48"><option value="">All statuses</option>{["ORDERED", "COLLECTED", "RESULTED", "VALIDATED", "CANCELLED"].map((s) => <option key={s}>{s}</option>)}</Select></div>
        {list.loading && list.items.length === 0 ? <Loading /> : (
          <Table head={["Order", "Patient", "Tests", "Priority", "Status", "Ordered"]} empty="No lab orders.">
            {list.items.map((o) => <Tr key={o.id}><Td href={`/lab/${o.id}`}>{o.orderNumber}</Td><Td>{o.patientName}</Td><Td>{o.items}</Td><Td><Status value={o.priority} /></Td><Td><Status value={o.status} /> {o.hasUnacknowledgedCritical && <Badge tone="danger">Critical</Badge>}</Td><Td>{stamp(o.createdAt)}</Td></Tr>)}
          </Table>
        )}
        <More onMore={list.more} loading={list.loading} />
      </Card>
    </Page>
  );
}

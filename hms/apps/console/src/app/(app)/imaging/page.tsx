"use client";

import Link from "next/link";
import { useState } from "react";
import { useFetch, usePaged } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, Loading, More, Notice, Page, Select, Status, Table, Td, Tr } from "@/components/ui";

type Row = { id: string; orderNumber: string; patientName: string; procedureName: string; modality: string; priority: string; status: string; createdAt: string; hasUnacknowledgedCritical: boolean };
type Critical = { orderId: string; orderNumber: string; patientName: string; procedureName: string; criticalNote: string };

export default function Imaging() {
  const { facilityId, can } = useSession();
  const [status, setStatus] = useState("");
  const list = usePaged<Row>(`/v1/imaging/orders?facilityId=${facilityId}${status ? `&status=${status}` : ""}`);
  const crit = useFetch<Critical[]>(`/v1/imaging/critical?facilityId=${facilityId}`);
  return (
    <Page title="Imaging" actions={<>
      {can("imaging:manage") && <Button variant="secondary" href="/imaging/procedures">Procedures</Button>}
      {can("orders:write") && <Button href="/imaging/new">New order</Button>}
    </>}>
      {crit.data && crit.data.length > 0 && (
        <Notice tone="danger" title="Critical findings awaiting acknowledgement">
          <ul className="pt-1">{crit.data.map((c) => <li key={c.orderId}><Link className="underline" href={`/imaging/${c.orderId}`}>{c.orderNumber}</Link> {c.patientName}: {c.procedureName}, {c.criticalNote}</li>)}</ul>
        </Notice>
      )}
      <Card pad={false}>
        <div className="border-b border-line p-3"><Select value={status} onChange={(e) => setStatus(e.target.value)} className="max-w-48"><option value="">All statuses</option>{["ORDERED", "PERFORMED", "REPORTED", "SIGNED", "CANCELLED"].map((s) => <option key={s}>{s}</option>)}</Select></div>
        {list.loading && list.items.length === 0 ? <Loading /> : (
          <Table head={["Order", "Patient", "Procedure", "Priority", "Status", "Ordered"]} empty="No imaging orders.">
            {list.items.map((o) => <Tr key={o.id}><Td href={`/imaging/${o.id}`}>{o.orderNumber}</Td><Td>{o.patientName}</Td><Td>{o.procedureName} <span className="text-faint">{o.modality}</span></Td><Td><Status value={o.priority} /></Td><Td><Status value={o.status} /> {o.hasUnacknowledgedCritical && <Badge tone="danger">Critical</Badge>}</Td><Td>{stamp(o.createdAt)}</Td></Tr>)}
          </Table>
        )}
        <More onMore={list.more} loading={list.loading} />
      </Card>
    </Page>
  );
}

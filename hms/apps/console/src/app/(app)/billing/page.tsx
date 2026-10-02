"use client";

import { useState } from "react";
import { usePaged } from "@/lib/api";
import { date, kes } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button, Card, Loading, More, Page, Select, Status, Table, Td, Tr } from "@/components/ui";

type Row = { id: string; invoiceNumber: string; patientName: string; status: string; payerType: string; total: number; amountPaid: number; createdAt: string };

export default function Billing() {
  const { facilityId, can } = useSession();
  const [status, setStatus] = useState("");
  const list = usePaged<Row>(`/v1/billing/invoices?facilityId=${facilityId}${status ? `&status=${status}` : ""}`);
  return (
    <Page title="Billing" actions={<>
      {can("billing:manage") && <Button variant="secondary" href="/billing/charges">Price list</Button>}
      {can("billing:post") && <Button href="/billing/new">New invoice</Button>}
    </>}>
      <Card pad={false}>
        <div className="border-b border-line p-2"><Select value={status} onChange={(e) => setStatus(e.target.value)} className="max-w-48"><option value="">All statuses</option>{["DRAFT", "ISSUED", "PARTIALLY_PAID", "PAID", "VOID"].map((s) => <option key={s}>{s}</option>)}</Select></div>
        {list.loading && list.items.length === 0 ? <Loading /> : (
          <Table head={["Invoice", "Patient", "Payer", "Status", "Total", "Paid", "Date"]} empty="No invoices.">
            {list.items.map((i) => <Tr key={i.id}><Td href={`/billing/${i.id}`}>{i.invoiceNumber}</Td><Td>{i.patientName}</Td><Td>{i.payerType}</Td><Td><Status value={i.status} /></Td><Td>{kes(i.total)}</Td><Td>{kes(i.amountPaid)}</Td><Td>{date(i.createdAt)}</Td></Tr>)}
          </Table>
        )}
        <More onMore={list.more} loading={list.loading} />
      </Card>
    </Page>
  );
}

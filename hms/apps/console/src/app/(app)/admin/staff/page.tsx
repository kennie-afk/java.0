"use client";

import { useState } from "react";
import { usePaged } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, Input, Loading, More, Page, Status, Table, Td, Tr } from "@/components/ui";

type Row = { id: string; email: string; fullName: string; cadre: string; status: string; licenceNo?: string };

export default function Staff() {
  const { can } = useSession();
  const [q, setQ] = useState("");
  const list = usePaged<Row>(`/v1/staff${q ? `?q=${encodeURIComponent(q)}` : ""}`);
  return (
    <Page title="Staff" actions={can("staff:manage") && <Button href="/admin/staff/new">Add staff member</Button>}>
      <Card pad={false}>
        <div className="border-b border-line p-3"><Input placeholder="Search name or email" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        {list.loading && list.items.length === 0 ? <Loading /> : (
          <Table head={["Name", "Email", "Cadre", "Licence", "Status"]} empty="No staff.">
            {list.items.map((s) => <Tr key={s.id}><Td href={`/admin/staff/${s.id}`}>{s.fullName}</Td><Td>{s.email}</Td><Td>{s.cadre}</Td><Td>{s.licenceNo}</Td><Td><Status value={s.status} /></Td></Tr>)}
          </Table>
        )}
        <More onMore={list.more} loading={list.loading} />
      </Card>
    </Page>
  );
}

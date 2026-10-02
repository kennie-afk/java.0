"use client";

import { useState } from "react";
import { usePaged } from "@/lib/api";
import { age, date } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, Input, Loading, More, Page, Table, Td, Tr } from "@/components/ui";
import type { PatientRow } from "@/components/PatientPicker";

export default function Patients() {
  const { can } = useSession();
  const [q, setQ] = useState("");
  const list = usePaged<PatientRow>(`/v1/patients${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ""}`);
  return (
    <Page title="Patients" actions={can("patients:write") && <Button href="/patients/new">Register patient</Button>}>
      <Card pad={false}>
        <div className="border-b border-line p-3"><Input placeholder="Search name, MRN, ID number or phone" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        {list.loading && list.items.length === 0 ? <Loading /> : (
          <Table head={["Name", "MRN", "Sex", "Born", "Phone"]} empty="No patients match.">
            {list.items.map((p) => (
              <Tr key={p.id}>
                <Td href={`/patients/${p.id}`}>{p.givenName} {p.familyName}</Td>
                <Td>{p.mrn} {p.restricted && <Badge tone="danger">Restricted</Badge>}</Td>
                <Td>{p.sex}</Td>
                <Td>{date(p.birthDate)} ({age(p.birthDate)})</Td>
                <Td>{p.phone}</Td>
              </Tr>
            ))}
          </Table>
        )}
        <More onMore={list.more} loading={list.loading} />
      </Card>
    </Page>
  );
}

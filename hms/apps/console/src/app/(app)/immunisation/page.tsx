"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { usePaged } from "@/lib/api";
import { date } from "@/lib/format";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Card, Field, More, Page, Select, Status, Table, Td, Tr } from "@/components/ui";

type Due = { patientId: string; patientName: string; vaccine: string; label: string; dueOn: string; daysOverdue: number; status: string; phone?: string };

export default function Immunisation() {
  const { facilityId } = useSession();
  const router = useRouter();
  const [picked, setPicked] = useState<PatientRow | null>(null);
  const [only, setOnly] = useState("");
  const due = usePaged<Due>(`/v1/mch/immunisation/due?facilityId=${facilityId}&horizonDays=14${only ? "&overdueOnly=true" : ""}`);
  return (
    <Page title="Immunisation" sub="Open a child's card, or work the list of doses due and overdue">
      <Card><Field label="Child"><PatientPicker value={picked} onPick={(p) => { setPicked(p); if (p) router.push(`/immunisation/${p.id}`); }} /></Field></Card>
      <div className="flex gap-2"><Select value={only} onChange={(e) => setOnly(e.target.value)} className="max-w-48"><option value="">Due in 14 days and overdue</option><option value="1">Overdue only</option></Select></div>
      <Card pad={false}>
        <Table head={["Child", "Dose", "Due", "Days overdue", "Phone", "Status"]} empty="No doses due.">
          {due.items.map((r) => <Tr key={`${r.patientId}${r.vaccine}`} tone={r.daysOverdue > 0 ? "warn" : undefined}><Td href={`/immunisation/${r.patientId}`}>{r.patientName}</Td><Td>{r.label}</Td><Td>{date(r.dueOn)}</Td><Td>{r.daysOverdue > 0 ? r.daysOverdue : ""}</Td><Td>{r.phone}</Td><Td><Status value={r.status} /></Td></Tr>)}
        </Table>
        <More onMore={due.more} loading={due.loading} />
      </Card>
    </Page>
  );
}

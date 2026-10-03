"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { usePaged } from "@/lib/api";
import { date } from "@/lib/format";
import { useSession } from "@/lib/session";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Card, Field, More, Page, Select, Table, Td, Tr } from "@/components/ui";

type Due = { patientId: string; patientName: string; method: string; methodLabel: string; nextDueOn: string; daysOverdue: number; phone?: string };

export default function FamilyPlanning() {
  const { facilityId } = useSession();
  const router = useRouter();
  const [picked, setPicked] = useState<PatientRow | null>(null);
  const [only, setOnly] = useState("");
  const due = usePaged<Due>(`/v1/mch/family-planning/due?facilityId=${facilityId}&horizonDays=14${only ? "&overdueOnly=true" : ""}`);
  return (
    <Page title="Family planning" sub="Open a patient's record, or work the list of contacts due and overdue">
      <Card><Field label="Patient"><PatientPicker value={picked} onPick={(p) => { setPicked(p); if (p) router.push(`/family-planning/${p.id}`); }} /></Field></Card>
      <div className="flex gap-2"><Select value={only} onChange={(e) => setOnly(e.target.value)} className="max-w-48"><option value="">Due in 14 days and overdue</option><option value="1">Overdue only</option></Select></div>
      <Card pad={false}>
        <Table head={["Patient", "Method", "Next contact", "Days overdue", "Phone"]} empty="No contacts due.">
          {due.items.map((r) => <Tr key={r.patientId} tone={r.daysOverdue > 0 ? "warn" : undefined}><Td href={`/family-planning/${r.patientId}`}>{r.patientName}</Td><Td>{r.methodLabel}</Td><Td>{date(r.nextDueOn)}</Td><Td>{r.daysOverdue > 0 ? r.daysOverdue : ""}</Td><Td>{r.phone}</Td></Tr>)}
        </Table>
        <More onMore={due.more} loading={due.loading} />
      </Card>
    </Page>
  );
}

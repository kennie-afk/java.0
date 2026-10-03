"use client";

import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button, Card, Confirm, ErrorNote, Loading, Page, Select, Status, Table, Td, Tr, useAction } from "@/components/ui";

type Req = { id: string; patientName: string; facility: string; preferredDate: string; reason: string; status: string; responseNote?: string; createdAt: string };

export default function PortalRequests() {
  const { facilityId } = useSession();
  const [status, setStatus] = useState("REQUESTED");
  const list = useFetch<Req[]>(`/v1/portal/requests?facilityId=${facilityId}${status ? `&status=${status}` : ""}`);
  const act = useAction();
  const answer = (id: string, s: "SCHEDULED" | "DECLINED", note: string) => act.run(async () => { await post(`/v1/portal/requests/${id}/resolve`, { status: s, note }); await list.reload(); });
  return (
    <Page title="Portal requests" sub="Appointment requests patients made from the portal. Answer each one; the patient sees your note.">
      <ErrorNote error={act.error} />
      <Card pad={false}>
        <div className="border-b border-line p-3"><Select value={status} onChange={(e) => setStatus(e.target.value)} className="max-w-48"><option value="">All</option>{["REQUESTED", "SCHEDULED", "DECLINED", "CANCELLED"].map((s) => <option key={s}>{s}</option>)}</Select></div>
        {list.loading ? <Loading /> : (
          <Table head={["Patient", "Wants", "Reason", "Asked", "Status", ""]} empty="No requests.">
            {(list.data ?? []).map((r) => (
              <Tr key={r.id}>
                <Td>{r.patientName}</Td><Td>{r.preferredDate}</Td><Td>{r.reason}</Td><Td>{stamp(r.createdAt)}</Td>
                <Td><Status value={r.status} /> {r.responseNote && <span className="text-xs text-muted">{r.responseNote}</span>}</Td>
                <Td>{r.status === "REQUESTED" && <span className="flex gap-1">
                  <Confirm label="Booked" variant="secondary" prompt="Say when and where it is booked. The patient sees this." needsReason minReason={3} onConfirm={(note) => answer(r.id, "SCHEDULED", note)} />
                  <Confirm label="Decline" prompt="Say why. The patient sees this." needsReason minReason={3} onConfirm={(note) => answer(r.id, "DECLINED", note)} />
                </span>}</Td>
              </Tr>
            ))}
          </Table>
        )}
      </Card>
      <Button variant="secondary" href="/appointments/new">Book an appointment</Button>
    </Page>
  );
}

"use client";

import { useRouter } from "next/navigation";
import { post, useFetch } from "@/lib/api";
import { time } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Loading, Page, Status, Table, Td, Tr, useAction } from "@/components/ui";

type Appt = { id: string; patientId: string; patientName: string; status: string; priority: string; queueNumber?: number; checkedInAt?: string; reason?: string; walkIn: boolean };

export default function Queue() {
  const { facilityId, can } = useSession();
  const router = useRouter();
  const q = useFetch<Appt[]>(`/v1/scheduling/queue?facilityId=${facilityId}`);
  const act = useAction();
  const call = (a: Appt) => act.run(async () => {
    if (can("clinical:write")) {
      const e = await post<{ id: string }>("/v1/clinical/encounters", { facilityId, patientId: a.patientId, type: "OPD", appointmentId: a.id });
      router.push(`/encounters/${e.id}`);
    } else {
      await post(`/v1/scheduling/appointments/${a.id}/start`);
      await q.reload();
    }
  });
  return (
    <Page title="Queue" sub="Most urgent first, then longest waiting." actions={<>
      <Button variant="secondary" onClick={() => void q.reload()}>Refresh</Button>
      {can("scheduling:write") && <Button href="/queue/walk-in">Add walk-in</Button>}
    </>}>
      <ErrorNote error={act.error ?? q.error} />
      <Card pad={false}>
        {q.loading && !q.data ? <Loading /> : (
          <Table head={["#", "Patient", "Priority", "Status", "Since", "Reason", ""]} empty="Nobody is waiting.">
            {(q.data ?? []).map((a) => (
              <Tr key={a.id}>
                <Td>{a.queueNumber}</Td><Td href={`/patients/${a.patientId}`}>{a.patientName}</Td><Td><Status value={a.priority} /></Td><Td><Status value={a.status} /></Td>
                <Td>{time(a.checkedInAt)}</Td><Td>{a.reason}</Td>
                <Td>{a.status === "CHECKED_IN" && can("scheduling:write") && <Button busy={act.busy} onClick={() => void call(a)}>Call in</Button>}</Td>
              </Tr>
            ))}
          </Table>
        )}
      </Card>
    </Page>
  );
}

"use client";

import { useState } from "react";
import { post, usePaged } from "@/lib/api";
import { stamp, today, addDays } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Input, Loading, More, Page, Status, Table, Td, Tr, useAction } from "@/components/ui";

type Appt = { id: string; patientId: string; patientName: string; startsAt: string; status: string; reason?: string };

export default function Appointments() {
  const { facilityId, can } = useSession();
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(addDays(today(), 7));
  const list = usePaged<Appt>(`/v1/scheduling/appointments?facilityId=${facilityId}&from=${from}&to=${to}`);
  const act = useAction();
  const move = (id: string, what: string) => act.run(async () => { await post(`/v1/scheduling/appointments/${id}/${what}`); list.reload(); });
  return (
    <Page title="Appointments" actions={<>{can("facilities:manage") && <Button variant="secondary" href="/appointments/clinics/new">New clinic</Button>}{can("scheduling:write") && <Button href="/appointments/new">Book appointment</Button>}</>}>
      <Card pad={false}>
        <div className="flex gap-2 border-b border-line p-2">
          <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="max-w-36" />
          <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="max-w-36" />
        </div>
        <ErrorNote error={act.error ?? list.error} />
        {list.loading && list.items.length === 0 ? <Loading /> : (
          <Table head={["When", "Patient", "Status", "Reason", ""]} empty="No appointments in this period.">
            {list.items.map((a) => (
              <Tr key={a.id}>
                <Td>{stamp(a.startsAt)}</Td><Td href={`/patients/${a.patientId}`}>{a.patientName}</Td><Td><Status value={a.status} /></Td><Td>{a.reason}</Td>
                <Td>
                  {can("scheduling:write") && a.status === "BOOKED" && (
                    <span className="flex gap-1"><Button variant="secondary" onClick={() => void move(a.id, "check-in")}>Check in</Button><Button variant="secondary" onClick={() => void move(a.id, "no-show")}>No-show</Button></span>
                  )}
                </Td>
              </Tr>
            ))}
          </Table>
        )}
        <More onMore={list.more} loading={list.loading} />
      </Card>
    </Page>
  );
}

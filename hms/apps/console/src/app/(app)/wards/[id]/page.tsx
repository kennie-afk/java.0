"use client";

import { use } from "react";
import { put, useFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Loading, Page, Status, Table, Td, Tr, useAction } from "@/components/ui";

type Bed = { id: string; wardName: string; label: string; status: string; admissionId?: string; patientName?: string };

export default function WardBeds({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const beds = useFetch<Bed[]>(`/v1/inpatient/wards/${id}/beds`);
  const act = useAction();
  const set = (bedId: string, status: string) => act.run(async () => { await put(`/v1/inpatient/beds/${bedId}/status`, { status }); await beds.reload(); });
  return (
    <Page title={beds.data?.[0]?.wardName ?? "Ward"}>
      <ErrorNote error={act.error ?? beds.error} />
      <Card pad={false}>
        {beds.loading ? <Loading /> : (
          <Table head={["Bed", "Status", "Patient", ""]} empty="No beds.">
            {(beds.data ?? []).map((b) => (
              <Tr key={b.id}><Td>{b.label}</Td><Td><Status value={b.status} /></Td><Td>{b.admissionId ? <a className="text-accent hover:underline" href={`/admissions/${b.admissionId}`}>{b.patientName}</a> : ""}</Td>
                <Td>{can("inpatient:write") && <span className="flex gap-1">
                  {b.status === "AVAILABLE" && <Button href={`/admissions/new?bedId=${b.id}`}>Admit here</Button>}
                  {b.status === "CLEANING" && <Button variant="secondary" onClick={() => void set(b.id, "AVAILABLE")}>Mark clean</Button>}
                  {b.status === "AVAILABLE" && <Button variant="secondary" onClick={() => void set(b.id, "OUT_OF_SERVICE")}>Out of service</Button>}
                  {b.status === "OUT_OF_SERVICE" && <Button variant="secondary" onClick={() => void set(b.id, "AVAILABLE")}>Back in service</Button>}</span>}</Td></Tr>
            ))}
          </Table>
        )}
      </Card>
    </Page>
  );
}

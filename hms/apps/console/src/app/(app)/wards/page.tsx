"use client";

import { useFetch, usePaged } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button, Card, Loading, More, Page, Table, Td, Tr } from "@/components/ui";

type Ward = { id: string; name: string; kind: string; beds: number; occupied: number; available: number; cleaning: number; outOfService: number };
type Adm = { id: string; admissionNumber: string; patientName: string; currentWard?: string; currentBed?: string; admittedAt: string; lengthOfStayDays: number; admittingDiagnosis?: string };

export default function Wards() {
  const { facilityId, can } = useSession();
  const wards = useFetch<Ward[]>(`/v1/inpatient/wards?facilityId=${facilityId}`);
  const census = usePaged<Adm>(`/v1/inpatient/admissions?facilityId=${facilityId}&status=ADMITTED`);
  return (
    <Page title="Wards and beds" actions={<>
      {can("facilities:manage") && <Button variant="secondary" href="/wards/new">New ward</Button>}
      {can("inpatient:write") && <Button href="/admissions/new">Admit patient</Button>}
    </>}>
      <Card pad={false}>
        {wards.loading ? <Loading /> : (
          <Table head={["Ward", "Type", "Beds", "Occupied", "Available", "Cleaning", "Out of service"]} empty="No wards yet.">
            {(wards.data ?? []).map((w) => <Tr key={w.id}><Td href={`/wards/${w.id}`}>{w.name}</Td><Td>{w.kind}</Td><Td>{w.beds}</Td><Td>{w.occupied}</Td><Td>{w.available}</Td><Td>{w.cleaning}</Td><Td>{w.outOfService}</Td></Tr>)}
          </Table>
        )}
      </Card>
      <Card title="Current inpatients" pad={false}>
        <Table head={["Admission", "Patient", "Bed", "Admitted", "Days", "Admitting diagnosis"]} empty="Nobody is admitted.">
          {census.items.map((a) => <Tr key={a.id}><Td href={`/admissions/${a.id}`}>{a.admissionNumber}</Td><Td>{a.patientName}</Td><Td>{a.currentWard} {a.currentBed}</Td><Td>{stamp(a.admittedAt)}</Td><Td>{a.lengthOfStayDays}</Td><Td>{a.admittingDiagnosis}</Td></Tr>)}
        </Table>
        <More onMore={census.more} loading={census.loading} />
      </Card>
    </Page>
  );
}

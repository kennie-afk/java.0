"use client";

import { use, useState } from "react";
import { post, useFetch, usePaged } from "@/lib/api";
import { age, date, kes, stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, ErrorNote, Grid, Input, KV, Loading, More, Page, Status, Table, Td, Tr, useAction } from "@/components/ui";
import { useRouter } from "next/navigation";
import { PortalAccess } from "@/components/PortalAccess";

type Patient = {
  id: string; givenName: string; otherNames?: string; familyName: string; sex: string; birthDate: string; phone?: string; county?: string; restricted: boolean; deceasedAt?: string;
  identifiers: { system: string; value: string }[]; contacts: { fullName: string; relationship: string; phone?: string }[];
};
type Allergy = { id: string; substance: string; severity: string; reaction?: string; status: string };
type Enc = { id: string; type: string; status: string; startedAt: string; chiefComplaint?: string; triageCategory?: string };
type Inv = { id: string; invoiceNumber: string; status: string; total: number; amountPaid: number; createdAt: string };

export default function PatientPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can, facilityId } = useSession();
  const router = useRouter();
  const [reason, setReason] = useState("");
  const p = useFetch<Patient>(`/v1/patients/${id}`, reason ? { "X-Access-Reason": reason } : undefined);
  const allergies = useFetch<Allergy[]>(can("clinical:read") && p.data ? `/v1/clinical/patients/${id}/allergies` : null);
  const encounters = usePaged<Enc>(can("clinical:read") && p.data ? `/v1/clinical/encounters?patientId=${id}` : null);
  const invoices = usePaged<Inv>(can("billing:read") && p.data ? `/v1/billing/invoices?patientId=${id}` : null);
  const start = useAction();

  if (p.error?.code === "reason_required") {
    return (
      <Page title="Restricted record">
        <Card title="State why you need to open this record. It is audited.">
          <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); setReason(e.currentTarget.reason.value); }}>
            <Input name="reason" minLength={10} required placeholder="At least 10 characters" />
            <Button type="submit">Open</Button>
          </form>
        </Card>
      </Page>
    );
  }
  if (p.loading || !p.data) return <Page title="Patient">{p.error ? <ErrorNote error={p.error} /> : <Loading />}</Page>;
  const d = p.data;
  const open = encounters.items.find((e) => e.status === "OPEN" && e.type === "OPD");

  return (
    <Page title={`${d.givenName} ${d.familyName}`} sub={`${d.sex} · ${date(d.birthDate)} (${age(d.birthDate)})`}
      actions={<>
        {d.restricted && <Badge tone="danger">Restricted</Badge>}
        {d.deceasedAt && <Badge tone="danger">Deceased</Badge>}
        {can("clinical:write") && !d.deceasedAt && (open
          ? <Button href={`/encounters/${open.id}`}>Open current visit</Button>
          : <Button busy={start.busy} onClick={() => void start.run(async () => {
              const e = await post<{ id: string }>("/v1/clinical/encounters", { facilityId, patientId: id, type: "OPD" });
              router.push(`/encounters/${e.id}`);
            })}>Start outpatient visit</Button>)}
        {can("billing:post") && <Button variant="secondary" href={`/billing/new?patientId=${id}`}>New invoice</Button>}
        {can("patients:merge") && <Button variant="secondary" href={`/patients/${id}/merge`}>Merge duplicate</Button>}
      </>}>
      <ErrorNote error={start.error} />
      <Grid cols={2}>
        <Card title="Details">
          <Grid cols={2}>
            <KV k="Phone" v={d.phone} /><KV k="County" v={d.county} />
            {d.identifiers.map((i) => <KV key={i.system + i.value} k={i.system.replaceAll("_", " ")} v={i.value} />)}
          </Grid>
          {d.contacts.length > 0 && <div className="mt-3 border-t border-line pt-2 text-sm">Next of kin: {d.contacts.map((c) => `${c.fullName} (${c.relationship}${c.phone ? ", " + c.phone : ""})`).join("; ")}</div>}
        </Card>
        {allergies.data && (
          <Card title="Allergies" actions={can("clinical:write") && <Button variant="secondary" href={`/patients/${id}/allergy`}>Add</Button>}>
            {allergies.data.length === 0 ? <div className="text-sm text-muted">None recorded.</div> : (
              <ul className="space-y-1 text-sm">
                {allergies.data.map((a) => <li key={a.id}><b>{a.substance}</b> <Badge tone={a.severity === "MILD" ? "neutral" : "danger"}>{a.severity}</Badge> {a.status !== "ACTIVE" && <Badge>{a.status}</Badge>} <span className="text-muted">{a.reaction}</span></li>)}
              </ul>
            )}
          </Card>
        )}
      </Grid>
      {can("portal:manage") && <PortalAccess patientId={id} />}
      {can("clinical:read") && (
        <Card title="Visits" pad={false}>
          <Table head={["Started", "Type", "Status", "Complaint"]} empty="No visits yet.">
            {encounters.items.map((e) => (
              <Tr key={e.id}><Td href={`/encounters/${e.id}`}>{stamp(e.startedAt)}</Td><Td>{e.type}</Td><Td><Status value={e.status} /></Td><Td>{e.chiefComplaint}</Td></Tr>
            ))}
          </Table>
          <More onMore={encounters.more} loading={encounters.loading} />
        </Card>
      )}
      {can("billing:read") && (
        <Card title="Invoices" pad={false}>
          <Table head={["Invoice", "Status", "Total", "Paid"]} empty="No invoices.">
            {invoices.items.map((i) => <Tr key={i.id}><Td href={`/billing/${i.id}`}>{i.invoiceNumber}</Td><Td><Status value={i.status} /></Td><Td>{kes(i.total)}</Td><Td>{kes(i.amountPaid)}</Td></Tr>)}
          </Table>
          <More onMore={invoices.more} loading={invoices.loading} />
        </Card>
      )}
    </Page>
  );
}

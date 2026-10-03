"use client";

import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { stamp } from "@/lib/format";
import { Button, Card, ErrorNote, Notice, Status, useAction } from "@/components/ui";

type Account = { hasAccount: boolean; status?: string; login?: string; lastLoginAt?: string };
type Invitation = { code: string; expiresAt: string; patientName: string };

/** Staff-side control of a patient's portal access: issue the one-time code (shown once), disable or re-enable. */
export function PortalAccess({ patientId }: { patientId: string }) {
  const acc = useFetch<Account>(`/v1/portal/accounts/${patientId}`);
  const [inv, setInv] = useState<Invitation | null>(null);
  const act = useAction();
  const a = acc.data;
  return (
    <Card title="Patient portal" description="Lets the patient see released results, their medicines and request appointments.">
      {a?.hasAccount ? (
        <div className="space-y-2 text-sm">
          <div className="flex items-center gap-2"><Status value={a.status ?? "ACTIVE"} /> <span className="text-muted">signs in as {a.login}{a.lastLoginAt ? `, last on ${stamp(a.lastLoginAt)}` : ""}</span></div>
          <Button variant="secondary" busy={act.busy} onClick={() => void act.run(async () => { await post(`/v1/portal/accounts/${patientId}/${a.status === "ACTIVE" ? "disable" : "enable"}`); await acc.reload(); })}>{a.status === "ACTIVE" ? "Disable access" : "Enable access"}</Button>
        </div>
      ) : (
        <div className="space-y-2 text-sm">
          <p className="text-muted">No portal account. Check who the patient is, then give them a one-time code. They also need their date of birth to use it.</p>
          <Button busy={act.busy} onClick={() => void act.run(async () => setInv(await post<Invitation>("/v1/portal/invitations", { patientId })))}>Create invitation code</Button>
          {inv && <Notice tone="warn" title={`Code for ${inv.patientName}: ${inv.code}`}>Shown once. Valid until {stamp(inv.expiresAt)}. The patient opens /portal/activate and enters the organisation, this code and their date of birth.</Notice>}
        </div>
      )}
      <ErrorNote error={act.error} />
    </Card>
  );
}

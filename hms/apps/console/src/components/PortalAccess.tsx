"use client";

import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { stamp } from "@/lib/format";
import { Button, Card, Confirm, ErrorNote, Notice, Status, useAction } from "@/components/ui";

type Message = { id: string; channel: string; recipient: string; template: string; status: string; attempts: number; lastError?: string; createdAt: string; sentAt?: string };

type Account = { hasAccount: boolean; status?: string; login?: string; lastLoginAt?: string };
type Invitation = { code: string; expiresAt: string; patientName: string; notices: string[] };

/** Staff-side control of a patient's portal access: issue the one-time code (shown once), disable or re-enable. */
export function PortalAccess({ patientId }: { patientId: string }) {
  const acc = useFetch<Account>(`/v1/portal/accounts/${patientId}`);
  const msgs = useFetch<Message[]>(`/v1/portal/accounts/${patientId}/notifications`);
  const [inv, setInv] = useState<Invitation | null>(null);
  const act = useAction();
  const a = acc.data;
  return (
    <Card title="Patient portal" description="Lets the patient see released results, their medicines and request appointments.">
      {a?.hasAccount ? (
        <div className="space-y-2 text-sm">
          <div className="flex items-center gap-2"><Status value={a.status ?? "ACTIVE"} /> <span className="text-muted">signs in as {a.login}{a.lastLoginAt ? `, last on ${stamp(a.lastLoginAt)}` : ""}</span></div>
          <Button variant="secondary" busy={act.busy} onClick={() => void act.run(async () => { await post(`/v1/portal/accounts/${patientId}/${a.status === "ACTIVE" ? "disable" : "enable"}`); await acc.reload(); })}>{a.status === "ACTIVE" ? "Disable access" : "Enable access"}</Button>
          <Confirm label="Forgot password: reset" prompt="Remove this portal account so a new invitation can be issued? Check who the patient is first. Their medical record is not touched." onConfirm={() => act.run(async () => { await post(`/v1/portal/accounts/${patientId}/reset`); await acc.reload(); })} />
        </div>
      ) : (
        <div className="space-y-2 text-sm">
          <p className="text-muted">No portal account. Check who the patient is, then give them a one-time code. They also need their date of birth to use it.</p>
          <Button busy={act.busy} onClick={() => void act.run(async () => { setInv(await post<Invitation>("/v1/portal/invitations", { patientId })); await msgs.reload(); })}>Create invitation code</Button>
          {inv && <Notice tone="warn" title={`Code for ${inv.patientName}: ${inv.code}`}>Shown once. Valid until {stamp(inv.expiresAt)}. The patient opens /portal/activate and enters the organisation, this code and their date of birth. {inv.notices.length ? `A notice was queued by ${inv.notices.join(" and ")}. It does not contain the code.` : "The patient has no phone or e-mail on file, so no notice was queued."}</Notice>}
        </div>
      )}
      {msgs.data && msgs.data.length > 0 && (
        <div className="mt-3 border-t border-line pt-2 text-sm">
          <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">Messages to the patient</p>
          {msgs.data.map((m) => <p key={m.id} className="flex flex-wrap items-center gap-2 py-0.5"><Status value={m.status} /><span>{m.template === "PORTAL_INVITED" ? "Invitation notice" : "Account created notice"} · {m.channel} to {m.recipient}</span>{m.lastError && <span className="text-muted">{m.lastError}</span>}</p>)}
        </div>
      )}
      <ErrorNote error={act.error} />
    </Card>
  );
}

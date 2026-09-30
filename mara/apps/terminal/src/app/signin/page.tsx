"use client";

import Link from "next/link";
import { useState } from "react";
import { Badge, Button, Card, Field, Input, KeyValue, Loading, Notice, PageHeader } from "@/components/ui";
import { useStaffSession } from "@/components/use-session";
import { useTerminalStatus } from "@/components/use-status";
import { signInStaff, signOut, type SignInResult } from "@/lib/staff";

export default function SignInPage() {
  const s = useTerminalStatus();
  const { loaded, session, refresh } = useStaffSession();
  const [staffNumber, setStaffNumber] = useState("");
  const [pin, setPin] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<SignInResult | null>(null);

  if (!s.loaded || !loaded) return <Loading />;

  if (!s.identity) {
    return (
      <div className="max-w-xl space-y-3">
        <PageHeader title="Staff sign-in" />
        <Notice tone="warn" title="This terminal is not enrolled">
          A terminal proves itself to identity-service with the key created at enrolment, so it must be enrolled before anyone
          can sign in. <Link href="/enrol" className="underline">Enrol this terminal</Link>.
        </Notice>
      </div>
    );
  }

  if (session) {
    return (
      <div className="max-w-xl space-y-3">
        <PageHeader title="Signed in" sub="Every sale from this till is now recorded against you." />
        <Card>
          <KeyValue
            rows={[
              ["Name", session.displayName],
              ["Staff number", <span key="n" className="font-mono">{session.staffNumber}</span>],
              ["Role", session.role],
              ["Verified by", session.via === "server" ? <Badge key="v" tone="good">identity-service</Badge> : <Badge key="v" tone="warn">this device (offline)</Badge>],
              ["Session ends", new Date(session.expiresAt).toLocaleString()]
            ]}
          />
        </Card>
        {session.via === "device" ? (
          <p className="text-xs text-muted">
            identity-service was not consulted. This sign-in used the PIN verifier saved on this device after your last online
            sign-in. If you have been suspended, or your PIN was changed, this device cannot know until it is next online.
          </p>
        ) : null}
        <div className="flex gap-2">
          <Link href="/sale" className="btn btn-primary">Go to sales</Link>
          <Button
            onClick={async () => {
              await signOut();
              setResult(null);
              setPin("");
              refresh();
            }}
          >
            Sign out
          </Button>
        </div>
      </div>
    );
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setResult(null);
    try {
      const r = await signInStaff(staffNumber, pin);
      setResult(r);
      setPin("");
      if (r.kind === "signed-in") refresh();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="max-w-sm space-y-3">
      <PageHeader title="Staff sign-in" sub="Enter your staff number and PIN. There are no shared logins." />
      <form onSubmit={submit} className="space-y-3" noValidate>
        <Card className="space-y-3">
          <Field label="Staff number">
            <Input value={staffNumber} onChange={(e) => setStaffNumber(e.target.value)} autoComplete="off" inputMode="numeric" autoFocus />
          </Field>
          <Field label="PIN">
            <Input value={pin} onChange={(e) => setPin(e.target.value)} type="password" autoComplete="off" inputMode="numeric" maxLength={8} />
          </Field>
          <Button variant="primary" type="submit" disabled={busy || staffNumber.trim() === "" || pin === ""}>
            {busy ? "Checking..." : "Sign in"}
          </Button>
        </Card>
      </form>
      {result?.kind === "refused" ? (
        <Notice tone="danger" title="Not accepted">
          Staff number or PIN not accepted. The message is the same whichever was wrong, on purpose.
        </Notice>
      ) : null}
      {result?.kind === "locked" ? (
        <Notice tone="danger" title="Locked">
          Too many wrong PINs. Try again after {new Date(result.until).toLocaleTimeString()} or ask a supervisor.
          {result.source === "device" ? " (Counted on this device while offline.)" : ""}
        </Notice>
      ) : null}
      {result?.kind === "no-device-record" ? (
        <Notice tone="warn" title="Sign in online once first">
          The network is unavailable and this device has not yet seen a successful sign-in for that staff number, so it has
          nothing to check the PIN against. Sign in once while connected; after that the same PIN works offline.
        </Notice>
      ) : null}
    </div>
  );
}

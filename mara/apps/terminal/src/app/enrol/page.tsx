"use client";

import Link from "next/link";
import { useState } from "react";
import { Button, Card, Field, Input, KeyValue, Loading, Notice, PageHeader } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";
import { CODE_MAX, enrol, type EnrolResult, LABEL_MAX } from "@/lib/enrol";
import { Ed25519UnsupportedError } from "@/lib/keys";

export default function EnrolPage() {
  const [refresh, setRefresh] = useState(0);
  const s = useTerminalStatus(refresh);
  const [code, setCode] = useState("");
  const [label, setLabel] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<EnrolResult | null>(null);
  const [fatal, setFatal] = useState<string | null>(null);

  if (!s.loaded) return <Loading />;

  if (s.identity) {
    return (
      <div className="max-w-xl space-y-3">
        <PageHeader title="Enrolment" sub="This browser is already a Mara terminal." />
        {result?.kind === "enrolled" ? (
          <Notice tone="good" title="Enrolled">
            identity-service accepted this terminal and issued its id.
          </Notice>
        ) : null}
        <Card>
          <KeyValue
            rows={[
              ["Terminal id", <span key="t" className="font-mono">{s.identity.terminalId}</span>],
              ["Label", s.identity.label],
              ["Public key (SPKI, base64)", <span key="k" className="break-all font-mono text-2xs">{s.identity.publicKeySpkiBase64}</span>]
            ]}
          />
        </Card>
        <p className="text-xs text-muted">
          Enrolment happens once per device. There is deliberately no re-enrol or reset here: replacing the key would orphan
          every journal entry it signed.
        </p>
        <Link href="/sale" className="btn btn-primary w-fit">
          Go to sales
        </Link>
      </div>
    );
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setResult(null);
    setFatal(null);
    try {
      const r = await enrol(code, label);
      setResult(r);
      if (r.kind === "enrolled") setRefresh((n) => n + 1);
    } catch (err) {
      setFatal(err instanceof Ed25519UnsupportedError || err instanceof Error ? err.message : "Enrolment failed.");
    } finally {
      setBusy(false);
    }
  }

  const ready = code.trim() !== "" && label.trim() !== "" && !busy;

  return (
    <div className="max-w-xl space-y-3">
      <PageHeader
        title="Enrol this terminal"
        sub="An owner issues a single-use enrolment code, valid for fifteen minutes. Enter it here with a name for this counter."
      />
      <form onSubmit={submit} className="space-y-3" noValidate>
        <Card className="space-y-3">
          <Field label="Enrolment code" hint="Case, spaces and the hyphen do not matter.">
            <Input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              maxLength={CODE_MAX}
              autoComplete="off"
              spellCheck={false}
              placeholder="XXXXX-XXXXX"
              className="font-mono"
            />
          </Field>
          <Field label="Terminal label" hint='What the owner will see, for example "Lane 4" or "Front desk".'>
            <Input value={label} onChange={(e) => setLabel(e.target.value)} maxLength={LABEL_MAX} autoComplete="off" />
          </Field>
          <p className="text-xs text-muted">
            An Ed25519 key pair is generated inside this browser now. The private key is created non-extractable and never
            leaves the device; only the public key is sent to identity-service.
          </p>
          <Button variant="primary" type="submit" disabled={!ready}>
            {busy ? "Enrolling..." : "Enrol"}
          </Button>
        </Card>
      </form>

      {fatal ? (
        <Notice tone="danger" title="Could not start enrolment">
          {fatal}
        </Notice>
      ) : null}
      {result ? <Outcome result={result} /> : null}
    </div>
  );
}

function Outcome({ result }: { result: EnrolResult }) {
  switch (result.kind) {
    case "refused":
      return (
        <Notice tone="danger" title={`Refused: ${result.error}`}>
          <p>{result.message}</p>
          <p className="mt-1 text-muted">
            identity-service gives the same answer for every refusal, on purpose, so an attacker cannot learn whether a
            code was wrong, expired or already used. If you are the owner, issue a new code. No key was kept from this
            attempt.
          </p>
        </Notice>
      );
    case "unreachable":
      return (
        <Notice tone="warn" title="identity-service could not be reached">
          {result.detail} Enrolment needs the network once. Selling does not, but a terminal must be enrolled before it can
          sell.
        </Notice>
      );
    case "offline":
      return (
        <Notice tone="warn" title="This device is offline">
          Enrolment is the one step that needs the network. Reconnect and try again.
        </Notice>
      );
    case "malformed":
      return (
        <Notice tone="danger" title="Rejected as malformed">
          {result.detail}
        </Notice>
      );
    case "unexpected":
      return (
        <Notice tone="danger" title={`Unexpected response (HTTP ${result.status})`}>
          {result.detail}
        </Notice>
      );
    case "enrolled":
      return null;
  }
}

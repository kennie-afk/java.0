"use client";

import { useState } from "react";
import { post } from "@/lib/api";
import { passwordProblem, PASSWORD_MIN } from "@/lib/rules";
import { Button, Card, ErrorNote, Field, Input, Notice, Page, useAction } from "@/components/ui";

export default function Account() {
  const [cur, setCur] = useState("");
  const [next, setNext] = useState("");
  const [again, setAgain] = useState("");
  const [done, setDone] = useState(false);
  const { busy, error, run } = useAction();
  const problem = passwordProblem(cur, next, again);
  return (
    <Page title="Change password" sub={`Use at least ${PASSWORD_MIN} characters.`}>
      <Card>
        <form className="max-w-sm space-y-3" onSubmit={(e) => { e.preventDefault(); setDone(false); void run(async () => { await post("/v1/auth/me/password", { currentPassword: cur, newPassword: next }); setCur(""); setNext(""); setAgain(""); setDone(true);
            // The server ended every session, this one included: clear the cookies and go to sign-in with the new password.
            await fetch("/api/session", { method: "DELETE" });
            window.setTimeout(() => { window.location.href = "/login"; }, 1500);
          }); }}>
          <Field label="Current password"><Input type="password" autoComplete="current-password" value={cur} onChange={(e) => setCur(e.target.value)} /></Field>
          <Field label="New password"><Input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} /></Field>
          <Field label="New password again" hint={next && problem ? problem : undefined}><Input type="password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} /></Field>
          <ErrorNote error={error} />
          {done && <Notice tone="info">Password changed. You were signed out on every device; sign in again with the new one.</Notice>}
          <Button type="submit" busy={busy} disabled={!!problem}>Change password</Button>
        </form>
      </Card>
    </Page>
  );
}

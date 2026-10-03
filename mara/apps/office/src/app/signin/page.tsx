"use client";

import { useState } from "react";
import { Logo } from "@/components/logo";
import { Button, Card, Field, Input, Notice } from "@/components/ui";

export default function SignIn() {
  const [credential, setCredential] = useState("");
  const [tenant, setTenant] = useState("");
  const [needsTenant, setNeedsTenant] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const res = await fetch("/api/session", {
      method: "POST",
      headers: { "content-type": "application/json", "x-office-csrf": "1" },
      body: JSON.stringify({ credential, tenant })
    });
    const body = await res.json().catch(() => ({}));
    setBusy(false);
    if (res.ok) {
      location.assign("/");
      return;
    }
    if (body.error === "needs_tenant") setNeedsTenant(true);
    setError(body.message ?? "Could not sign in.");
  }

  return (
    <div className="space-y-3">
      <Logo size={32} />
      <Card>
        <h1 className="mb-1 text-xl font-semibold">Back office</h1>
        <p className="mb-3 text-xs text-muted">Paste the credential Mara gave you for your shop. It is checked, then kept only in an encrypted cookie in this browser.</p>
        <form onSubmit={submit} className="space-y-3">
          <Field label="Credential" hint="Starts mop_. It expires, and can be replaced or revoked at any time.">
            <Input type="password" autoComplete="off" spellCheck={false} value={credential} onChange={(e) => setCredential(e.target.value)} required />
          </Field>
          {needsTenant ? (
            <Field label="Tenant id" hint="Only for a platform credential, which is not tied to one shop.">
              <Input value={tenant} onChange={(e) => setTenant(e.target.value)} required />
            </Field>
          ) : null}
          {error ? <Notice tone="danger">{error}</Notice> : null}
          <Button variant="primary" type="submit" disabled={busy || credential.length < 20}>{busy ? "Checking…" : "Sign in"}</Button>
        </form>
      </Card>
    </div>
  );
}

"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api";
import { Button, Card, ErrorNote, Field, Input, useAction } from "@/components/ui";

export default function PortalLogin() {
  const router = useRouter();
  const [f, setF] = useState({ organisation: "", login: "", password: "" });
  const { busy, error, run } = useAction();
  useEffect(() => {
    try { setF((x) => ({ ...x, organisation: localStorage.getItem("hms_portal_org") ?? "" })); } catch { /* storage may be blocked */ }
  }, []);
  return (
    <Card title="Sign in" description="Use the e-mail address or phone number you chose when you set up your account.">
      <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); void run(async () => {
        const res = await fetch("/api/portal-session", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(f) });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new ApiError(res.status, data);
        try { localStorage.setItem("hms_portal_org", f.organisation); } catch { /* ignore */ }
        router.push("/portal");
      }); }}>
        <Field label="Hospital or clinic code"><Input required value={f.organisation} onChange={(e) => setF({ ...f, organisation: e.target.value })} autoCapitalize="none" /></Field>
        <Field label="E-mail or phone"><Input required value={f.login} onChange={(e) => setF({ ...f, login: e.target.value })} autoComplete="username" /></Field>
        <Field label="Password"><Input required type="password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} autoComplete="current-password" /></Field>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy}>Sign in</Button>
      </form>
      <p className="pt-3 text-sm text-muted">First time? Ask the front desk for an invitation code, then <a className="underline" href="/portal/activate">set up your account</a>.</p>
    </Card>
  );
}

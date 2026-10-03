"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { Button, Card, ErrorNote, Field, Input, useAction } from "@/components/ui";

export default function PortalActivate() {
  const router = useRouter();
  const [f, setF] = useState({ organisation: "", code: "", birthDate: "", login: "", password: "", again: "" });
  const [mismatch, setMismatch] = useState(false);
  const { busy, error, run } = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Card title="Set up your account" description="You need the code the front desk gave you and your date of birth.">
      <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); setMismatch(f.password !== f.again); if (f.password !== f.again) return; void run(async () => {
        const res = await fetch("/api/portal-activate", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ organisation: f.organisation, code: f.code, birthDate: f.birthDate, login: f.login, password: f.password }) });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new ApiError(res.status, data);
        router.push("/portal/login");
      }); }}>
        <Field label="Hospital or clinic code"><Input required value={f.organisation} onChange={set("organisation")} autoCapitalize="none" /></Field>
        <Field label="Invitation code"><Input required value={f.code} onChange={set("code")} placeholder="XXXXX-XXXXX" /></Field>
        <Field label="Date of birth"><Input required type="date" value={f.birthDate} onChange={set("birthDate")} /></Field>
        <Field label="E-mail or phone to sign in with"><Input required value={f.login} onChange={set("login")} autoComplete="username" /></Field>
        <Field label="Password" hint="At least 10 characters"><Input required type="password" minLength={10} value={f.password} onChange={set("password")} autoComplete="new-password" /></Field>
        <Field label="Password again"><Input required type="password" value={f.again} onChange={set("again")} autoComplete="new-password" /></Field>
        {mismatch && <p className="text-sm text-danger">The two passwords differ.</p>}
        <ErrorNote error={error} />
        <Button type="submit" busy={busy}>Create account</Button>
      </form>
    </Card>
  );
}

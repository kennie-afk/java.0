"use client";

import Link from "next/link";
import { useState } from "react";
import { Button, Card, ErrorNote, Field, Grid, Input, useAction } from "@/components/ui";
import { ApiError } from "@/lib/api";

export default function Setup() {
  const [f, setF] = useState({ organisationName: "", slug: "", facilityName: "", mflCode: "", county: "", fullName: "", email: "", password: "" });
  const [done, setDone] = useState(false);
  const { busy, error, run } = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    void run(async () => {
      const res = await fetch("/api/setup", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ organisationName: f.organisationName, slug: f.slug, facility: { name: f.facilityName, mflCode: f.mflCode || undefined, county: f.county || undefined }, admin: { fullName: f.fullName, email: f.email, password: f.password } })
      });
      if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => ({})));
      setDone(true);
    });
  };
  if (done) {
    return (
      <div className="flex min-h-screen items-center justify-center p-4">
        <Card title="Organisation created"><p className="pb-3 text-xs">You can now sign in as {f.email}.</p><Button href="/login">Go to sign in</Button></Card>
      </div>
    );
  }
  return (
    <div className="mx-auto max-w-2xl space-y-4 p-4">
      <div><div className="text-2xl font-semibold text-accent">HMS</div><div className="text-xs text-muted">Set up an organisation, its first facility and its administrator.</div></div>
      <form onSubmit={submit} className="space-y-4">
        <Card title="Organisation"><Grid cols={2}>
          <Field label="Name"><Input required value={f.organisationName} onChange={set("organisationName")} /></Field>
          <Field label="Address slug" hint="lowercase letters, digits and hyphens"><Input required pattern="[a-z0-9][a-z0-9-]{1,62}" value={f.slug} onChange={set("slug")} /></Field>
        </Grid></Card>
        <Card title="First facility"><Grid cols={3}>
          <Field label="Name"><Input required value={f.facilityName} onChange={set("facilityName")} /></Field>
          <Field label="MFL code" hint="Master Health Facility List"><Input value={f.mflCode} onChange={set("mflCode")} /></Field>
          <Field label="County"><Input value={f.county} onChange={set("county")} /></Field>
        </Grid></Card>
        <Card title="Administrator"><Grid cols={3}>
          <Field label="Full name"><Input required value={f.fullName} onChange={set("fullName")} /></Field>
          <Field label="Email"><Input type="email" required value={f.email} onChange={set("email")} /></Field>
          <Field label="Password" hint="At least 12 characters"><Input type="password" required minLength={12} value={f.password} onChange={set("password")} /></Field>
        </Grid></Card>
        <ErrorNote error={error} />
        <div className="flex items-center gap-3"><Button type="submit" busy={busy}>Create</Button><Link href="/login" className="text-xs text-accent hover:underline">Back to sign in</Link></div>
      </form>
    </div>
  );
}

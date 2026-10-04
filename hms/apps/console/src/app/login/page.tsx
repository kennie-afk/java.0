"use client";

import Link from "next/link";
import { useState } from "react";
import { Icon, type IconName } from "@/components/icons";
import { Button, ErrorNote, Field, Input, useAction } from "@/components/ui";
import { ApiError } from "@/lib/api";

const POINTS: { icon: IconName; title: string; body: string }[] = [
  { icon: "patients", title: "One patient record", body: "Registration, visits, orders and results in a single chart." },
  { icon: "flask", title: "Lab and pharmacy in the loop", body: "Critical results are flagged until someone acknowledges them." },
  { icon: "shield", title: "Claims you can trust", body: "Readiness checks before a claim leaves the building, with an audit trail on every access." }
];

export default function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const { busy, error, run } = useAction();

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    void run(async () => {
      const res = await fetch("/api/session", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password }) });
      if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => ({})));
      window.location.href = "/";
    });
  };

  return (
    <div className="glow grid min-h-screen lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
      <aside className="hidden bg-[linear-gradient(135deg,#0053a3,#02386e)] p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-white">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/logo-icon.flat.svg" alt="" className="h-8 w-8" />
          </span>
          <span className="font-[family-name:var(--font-display)] text-2xl font-bold">HMS</span>
        </div>
        <div className="max-w-xl">
          <h1 className="text-4xl font-bold leading-tight text-white">Run the whole facility, from the front desk to the claim.</h1>
          <ul className="mt-10 space-y-6">
            {POINTS.map((p) => (
              <li key={p.title} className="flex gap-4">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white/12"><Icon name={p.icon} className="h-5 w-5" /></span>
                <span>
                  <span className="block text-lg font-semibold">{p.title}</span>
                  <span className="block text-base leading-snug text-white/75">{p.body}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <p className="text-sm text-white/75">Health management console</p>
      </aside>
      <main className="flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-md">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo-icon.flat.svg" alt="" className="mb-4 h-12 w-12 lg:hidden" />
          <h2 className="text-3xl font-bold">Welcome back</h2>
          <p className="mb-6 mt-1.5 text-base text-muted">Sign in to your facility.</p>
          <form onSubmit={submit} className="space-y-4 rounded-xl border border-line bg-surface p-6 shadow-[var(--shadow-card)]">
            <Field label="Email"><Input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
            <Field label="Password"><Input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
            <ErrorNote error={error} />
            <Button type="submit" busy={busy}>Sign in</Button>
            <p className="text-sm"><Link href="/setup" className="font-semibold text-accent hover:underline">Set up a new organisation</Link></p>
          </form>
        </div>
      </main>
    </div>
  );
}

"use client";

import Link from "next/link";
import { useState } from "react";
import { Button, ErrorNote, Field, Input, useAction } from "@/components/ui";
import { ApiError } from "@/lib/api";

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
    <div className="flex min-h-screen items-center justify-center p-4">
      <form onSubmit={submit} className="w-full max-w-xs space-y-3 rounded-lg border border-line bg-surface p-4">
        <div>
          <div className="text-2xl font-semibold text-accent">HMS</div>
          <div className="text-xs text-muted">Sign in to your facility</div>
        </div>
        <Field label="Email"><Input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
        <Field label="Password"><Input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy}>Sign in</Button>
        <div className="text-xs"><Link href="/setup" className="text-accent hover:underline">Set up a new organisation</Link></div>
      </form>
    </div>
  );
}

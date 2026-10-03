"use client";

import { useState } from "react";
import { Badge, Button, Card, Empty, Field, Notice, PageHeader, Select, statusTone } from "@/components/ui";
import { useOp } from "@/components/use-op";
import { call } from "@/lib/api";
import type { Branch, Staff, Terminal } from "@/lib/types";

export default function Tills() {
  const terminals = useOp<Terminal[]>("terminals");
  const branches = useOp<Branch[]>("branches");
  const staff = useOp<Staff[]>("staff");
  const [branchId, setBranchId] = useState("");
  const [code, setCode] = useState<{ code: string; expiresAt: string } | null>(null);
  const [message, setMessage] = useState<{ tone: "danger" | "good"; text: string } | null>(null);
  const owner = staff.data?.find((s) => s.role === "OWNER" && s.status === "ACTIVE");
  const branchName = (id: string) => branches.data?.find((b) => b.id === id)?.name ?? id;
  const chosenBranch = branchId || branches.data?.[0]?.id || "";

  async function setStatus(t: Terminal, status: string) {
    if (status !== "ACTIVE" && !confirm(`${status === "REVOKED" ? "Revoke" : "Suspend"} ${t.label}? It will stop being believed within about 30 seconds.`)) return;
    try {
      await call("terminal.status", { query: { id: t.id }, body: { status } });
      setMessage({ tone: "good", text: `${t.label} is now ${status.toLowerCase()}.` });
      terminals.reload();
    } catch (e) {
      setMessage({ tone: "danger", text: e instanceof Error ? e.message : "Failed." });
    }
  }

  async function issue() {
    if (!owner) return setMessage({ tone: "danger", text: "A shop needs an active owner before it can add a till." });
    try {
      const r = await call<{ code: string; expiresAt: string }>("enrolment.issue", { body: { branchId: chosenBranch, issuedBy: owner.id } });
      setCode(r);
      setMessage(null);
    } catch (e) {
      setMessage({ tone: "danger", text: e instanceof Error ? e.message : "Failed." });
    }
  }

  return (
    <div className="space-y-3">
      <PageHeader title="Tills" sub="A till sells and signs every sale with its own key. Suspend one that is lost or stolen." />
      {message ? <Notice tone={message.tone}>{message.text}</Notice> : null}
      {terminals.error ? <Notice tone="danger">{terminals.error}</Notice> : null}
      <Card>
        <h2 className="mb-2 text-base font-semibold">Add a till</h2>
        <div className="flex flex-wrap items-end gap-3">
          <Field label="Branch">
            <Select value={chosenBranch} onChange={(e) => setBranchId(e.target.value)}>
              {(branches.data ?? []).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </Select>
          </Field>
          <Button variant="primary" onClick={issue} disabled={!chosenBranch}>Get an enrolment code</Button>
        </div>
        {code ? (
          <div className="mt-3"><Notice tone="good" title="Enter this on the new till">
            <div className="text-3xl font-semibold tracking-wider tabular-nums">{code.code}</div>
            Open the till, go to Enrolment, and enter it. It works once, expires {new Date(code.expiresAt).toLocaleTimeString()}, and cannot be shown again.
          </Notice></div>
        ) : null}
      </Card>
      <Card className="p-0">
        {(terminals.data ?? []).length === 0 && !terminals.loading ? <Empty>No tills yet. Add one above.</Empty> : (
          <table className="tbl">
            <thead><tr><th>Till</th><th>Branch</th><th>Status</th><th>Enrolled</th><th>Last heard from</th><th /></tr></thead>
            <tbody>
              {(terminals.data ?? []).map((t) => (
                <tr key={t.id}>
                  <td><div className="font-semibold">{t.label}</div><div className="text-2xs text-muted">{t.id}</div></td>
                  <td>{branchName(t.branch_id)}</td>
                  <td><Badge tone={statusTone(t.status)}>{t.status.toLowerCase()}</Badge></td>
                  <td>{t.enrolled_at ? new Date(t.enrolled_at).toLocaleDateString() : ""}</td>
                  <td>{t.last_seen_at ? new Date(t.last_seen_at).toLocaleString() : "never"}</td>
                  <td className="text-right whitespace-nowrap">
                    {t.status === "ACTIVE" ? <Button variant="danger" onClick={() => setStatus(t, "SUSPENDED")}>Suspend</Button> : null}
                    {t.status === "SUSPENDED" ? <Button onClick={() => setStatus(t, "ACTIVE")}>Reactivate</Button> : null}
                    {t.status !== "REVOKED" ? <Button variant="danger" className="ml-1" onClick={() => setStatus(t, "REVOKED")}>Revoke</Button> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}

"use client";

import { useState } from "react";
import { Badge, Button, Card, Empty, Field, Input, Notice, PageHeader, Select, statusTone } from "@/components/ui";
import { useOp } from "@/components/use-op";
import { call } from "@/lib/api";
import type { Branch, Staff } from "@/lib/types";

export default function StaffPage() {
  const staff = useOp<Staff[]>("staff");
  const branches = useOp<Branch[]>("branches");
  const [form, setForm] = useState({ displayName: "", role: "CASHIER", staffNumber: "", pin: "", branchId: "" });
  const [message, setMessage] = useState<{ tone: "danger" | "good"; text: string } | null>(null);
  const branchName = (id: string | null) => (id ? branches.data?.find((b) => b.id === id)?.name ?? id : "All branches");
  const branchId = form.branchId || branches.data?.[0]?.id || "";

  async function add(e: React.FormEvent) {
    e.preventDefault();
    try {
      await call("staff.create", { body: { ...form, branchId } });
      setMessage({ tone: "good", text: `${form.displayName} added. Give them their PIN in person.` });
      setForm({ displayName: "", role: "CASHIER", staffNumber: "", pin: "", branchId: "" });
      staff.reload();
    } catch (err) {
      setMessage({ tone: "danger", text: err instanceof Error ? err.message : "Failed." });
    }
  }

  async function setStatus(s: Staff, status: string) {
    if (status !== "ACTIVE" && !confirm(`${status === "REVOKED" ? "Remove" : "Suspend"} ${s.display_name}?`)) return;
    try {
      await call("staff.status", { query: { id: s.id }, body: { status } });
      staff.reload();
    } catch (err) {
      setMessage({ tone: "danger", text: err instanceof Error ? err.message : "Failed." });
    }
  }

  return (
    <div className="space-y-3">
      <PageHeader title="Staff" sub="Who may sign in at a till. PINs are never shown again after they are set." />
      {message ? <Notice tone={message.tone}>{message.text}</Notice> : null}
      <Card>
        <h2 className="mb-2 text-base font-semibold">Add a person</h2>
        <form onSubmit={add} className="grid grid-cols-1 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <Field label="Name"><Input required maxLength={80} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} /></Field>
          <Field label="Staff number" hint="Letters, digits, hyphen"><Input required maxLength={20} value={form.staffNumber} onChange={(e) => setForm({ ...form, staffNumber: e.target.value })} /></Field>
          <Field label="Role">
            <Select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
              <option value="CASHIER">Cashier</option><option value="SUPERVISOR">Supervisor</option><option value="MANAGER">Manager</option>
            </Select>
          </Field>
          <Field label="Branch">
            <Select value={branchId} onChange={(e) => setForm({ ...form, branchId: e.target.value })}>
              {(branches.data ?? []).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </Select>
          </Field>
          <Field label="PIN"><Input required type="password" autoComplete="new-password" inputMode="numeric" value={form.pin} onChange={(e) => setForm({ ...form, pin: e.target.value })} /></Field>
          <div className="flex items-end"><Button variant="primary" type="submit" disabled={!branchId}>Add</Button></div>
        </form>
      </Card>
      {staff.error ? <Notice tone="danger">{staff.error}</Notice> : null}
      <Card className="p-0">
        {(staff.data ?? []).length === 0 && !staff.loading ? <Empty>No staff yet.</Empty> : (
          <table className="tbl">
            <thead><tr><th>Number</th><th>Name</th><th>Role</th><th>Branch</th><th>Status</th><th /></tr></thead>
            <tbody>
              {(staff.data ?? []).map((s) => (
                <tr key={s.id}>
                  <td>{s.staff_number}</td><td className="font-semibold">{s.display_name}</td><td>{s.role.toLowerCase()}</td><td>{branchName(s.branch_id)}</td>
                  <td>
                    <Badge tone={statusTone(s.status)}>{s.status.toLowerCase()}</Badge>
                    {s.locked_until && new Date(s.locked_until) > new Date() ? <Badge tone="warn">locked after wrong PINs</Badge> : null}
                  </td>
                  <td className="text-right whitespace-nowrap">
                    {s.role !== "OWNER" && s.status === "ACTIVE" ? <Button variant="danger" onClick={() => setStatus(s, "SUSPENDED")}>Suspend</Button> : null}
                    {s.role !== "OWNER" && s.status === "SUSPENDED" ? <Button onClick={() => setStatus(s, "ACTIVE")}>Reactivate</Button> : null}
                    {s.role !== "OWNER" && s.status !== "REVOKED" ? <Button variant="danger" className="ml-1" onClick={() => setStatus(s, "REVOKED")}>Remove</Button> : null}
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

"use client";

import { Badge, Card, Empty, Notice, PageHeader } from "@/components/ui";
import { useOp } from "@/components/use-op";
import type { AuditRow } from "@/lib/types";

export default function Audit() {
  const audit = useOp<AuditRow[]>("audit", { limit: "200" });
  return (
    <div className="space-y-3">
      <PageHeader title="Audit trail" sub="Who signed in, who was refused, and every change to staff and tills. It cannot be edited." />
      {audit.error ? <Notice tone="danger">{audit.error}</Notice> : null}
      <Card className="p-0">
        {(audit.data ?? []).length === 0 && !audit.loading ? <Empty>No activity recorded yet.</Empty> : (
          <table className="tbl">
            <thead><tr><th>When</th><th>Who</th><th>Till</th><th>What</th><th>Result</th><th>Detail</th></tr></thead>
            <tbody>
              {(audit.data ?? []).map((r) => (
                <tr key={r.id}>
                  <td className="whitespace-nowrap">{new Date(r.occurred_at).toLocaleString()}</td><td>{r.actor}</td>
                  <td className="text-2xs">{r.terminal_id ?? ""}</td><td>{r.action.toLowerCase().replaceAll("_", " ")}</td>
                  <td><Badge tone={r.outcome === "SUCCESS" || r.outcome === "OK" ? "good" : "warn"}>{r.outcome.toLowerCase()}</Badge></td>
                  <td className="text-2xs">{r.detail ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}

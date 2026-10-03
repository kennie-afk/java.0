"use client";

import { Card, Empty, Notice, PageHeader, SectionTitle } from "@/components/ui";
import { useOp } from "@/components/use-op";
import type { CoreException, SyncException } from "@/lib/types";

const EXPLAIN: Record<string, string> = {
  FISCAL_DUPLICATE: "Two sales claimed the same fiscal number. Only the first holds it.",
  FISCAL_OUT_OF_LEASE: "A sale used a fiscal number the till was never given.",
  SALE_UNBALANCED: "A sale's lines and payments did not add up. It is parked in a suspense account, not lost.",
  SALE_UNPOSTABLE: "A sale could not be posted to the books.",
  GAP: "The server is missing sales between two numbers from this till. They will arrive when the till next syncs.",
  FORKED_SEQUENCE: "A till sent two different sales with the same number. This is serious: look at that till.",
  BROKEN_LINK: "A sale does not follow on from the one before it in the till's record.",
  BAD_SIGNATURE: "A sale's signature does not match the till's key.",
  BAD_BODY_DIGEST: "A sale's contents do not match what the till signed.",
  BAD_DIGEST: "A sale's record does not match its own fingerprint.",
  NON_MONOTONIC_CLOCK: "A till's clock ran backwards.",
  SALE_INCONSISTENT: "A sale's arithmetic does not add up."
};

function Table({ rows }: { rows: (CoreException | SyncException)[] }) {
  if (rows.length === 0) return <Empty>Nothing needs a look.</Empty>;
  return (
    <table className="tbl">
      <thead><tr><th>Raised</th><th>Till</th><th>Sale no.</th><th>What</th><th>Detail</th></tr></thead>
      <tbody>
        {rows.map((r) => (
          <tr key={`${r.id}`}>
            <td>{new Date(r.raisedAt).toLocaleString()}</td><td className="text-2xs">{r.terminalId}</td><td>{r.sequence}</td>
            <td><div className="font-semibold">{r.kind.toLowerCase().replaceAll("_", " ")}</div><div className="text-2xs text-muted">{EXPLAIN[r.kind] ?? ""}</div></td>
            <td className="text-2xs">{r.detail}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default function Exceptions() {
  const core = useOp<CoreException[]>("core.exceptions", { open: "true", limit: "200" });
  const sync = useOp<SyncException[]>("sync.exceptions", { open: "true", limit: "200" });
  return (
    <div className="space-y-3">
      <PageHeader title="Exceptions" sub="Things the servers found that a person should look at. Nothing here means a sale was lost." />
      {core.error ? <Notice tone="danger">{core.error}</Notice> : null}
      {sync.error ? <Notice tone="danger">{sync.error}</Notice> : null}
      <Card className="p-0"><div className="p-3 pb-0"><SectionTitle>In the books</SectionTitle></div><Table rows={core.data ?? []} /></Card>
      <Card className="p-0"><div className="p-3 pb-0"><SectionTitle>Arriving from the tills</SectionTitle></div><Table rows={sync.data ?? []} /></Card>
    </div>
  );
}

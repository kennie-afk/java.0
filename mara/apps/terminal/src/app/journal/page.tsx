"use client";

import Link from "next/link";
import { Badge, EmptyState, LinkButton, Loading, Notice, PageHeader, Pager, Table, usePagedList } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";
import { getHead, listEntries, readAscending } from "@/lib/journal-store";
import { getIdentity } from "@/lib/terminal-store";
import { Button } from "@/components/ui";
import { format, money } from "@/lib/money";

/**
 * Downloads the whole journal with the terminal's public key, as one JSON file: the bundle a
 * sync service will one day receive, and what an auditor can verify without this device.
 */
async function exportBundle() {
  const identity = await getIdentity();
  const head = await getHead();
  const entries = [];
  for (let after = 0; ; ) {
    const rows = await readAscending(after, 200);
    if (rows.length === 0) break;
    entries.push(...rows);
    after = rows[rows.length - 1].sequence;
  }
  const bundle = {
    format: "mara.journal-export.v1",
    terminalId: identity?.terminalId ?? null,
    publicKeySpkiBase64: identity?.publicKeySpkiBase64 ?? null,
    head,
    entries
  };
  const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `mara-journal-${identity?.terminalId ?? "terminal"}.json`;
  a.click();
  URL.revokeObjectURL(a.href);
}

export default function JournalPage() {
  const s = useTerminalStatus();
  const list = usePagedList((before: number | null) => listEntries({ before, limit: 25 }), []);
  if (!s.loaded || (!list.page && !list.error)) return <Loading />;

  return (
    <div className="space-y-3">
      <PageHeader
        title="Journal"
        sub="Every sale, in the order this terminal recorded it. Each entry commits to the one before it and is signed. Read-only: there is no way to edit or delete an entry."
        actions={
          <span className="flex gap-2">
            <Button onClick={exportBundle}>Export</Button>
            <LinkButton href="/journal/verify">Verify chain</LinkButton>
          </span>
        }
      />
      {list.error ? <Notice tone="danger">{list.error}</Notice> : null}
      {list.page && list.page.items.length === 0 ? (
        <div className="card">
          <EmptyState title="The journal is empty">No sale has been recorded on this terminal yet.</EmptyState>
        </div>
      ) : (
        <Table head={[{ label: "Seq", num: true }, { label: "Time" }, { label: "Total", num: true }, { label: "Fiscal" }, { label: "Digest" }, { label: "" }]}>
          {list.page?.items.map((e) => (
            <tr key={e.sequence}>
              <td className="num font-medium">{e.sequence}</td>
              <td>{new Date(e.epochSecond * 1000 + Math.floor(e.nano / 1e6)).toLocaleString()}</td>
              <td className="num">{format(money(BigInt(e.sale.totalMinor), e.sale.currency))}</td>
              <td>{e.sale.fiscal.status === "NUMBERED" ? <Badge tone="good">#{e.sale.fiscal.number}</Badge> : <Badge tone="warn">FISCAL_PENDING</Badge>}</td>
              <td className="font-mono text-2xs text-muted">{e.digest.slice(0, 16)}...</td>
              <td className="num">
                <Link href={`/receipt?seq=${e.sequence}`} className="text-accent">Receipt</Link>
              </td>
            </tr>
          ))}
        </Table>
      )}
      <Pager shown={list.page?.items.length ?? 0} hasPrev={list.hasPrev} hasNext={list.hasNext} onPrev={list.prev} onNext={list.next} noun="entries" />
    </div>
  );
}

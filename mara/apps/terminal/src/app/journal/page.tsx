"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Badge, EmptyState, LinkButton, Loading, Notice, PageHeader, Pager, Table, usePagedList } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";
import { getHead, listEntries, readAscending } from "@/lib/journal-store";
import { getIdentity } from "@/lib/terminal-store";
import { Button } from "@/components/ui";
import { format, money } from "@/lib/money";
import { Card, KeyValue } from "@/components/ui";
import { getPendingReturns, getSyncState, syncCycle } from "@/lib/sync";
import type { SyncState } from "@/lib/records";

/** How this journal stands with the server: what it holds a verified copy of, and why not more. */
function SyncCard({ lastSequence }: { lastSequence: number }) {
  const [state, setState] = useState<SyncState | null>(null);
  const [pending, setPending] = useState(0);
  const [busy, setBusy] = useState(false);
  const refresh = useCallback(async () => {
    setState(await getSyncState());
    setPending((await getPendingReturns()).length);
  }, []);
  useEffect(() => {
    void refresh();
    const t = setInterval(() => void refresh(), 5000);
    return () => clearInterval(t);
  }, [refresh, lastSequence]);
  if (!state) return null;
  const behind = Math.max(0, lastSequence - state.syncedThrough);
  return (
    <Card className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold">Server copy</span>
        <Button
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            await syncCycle().catch(() => null);
            await refresh();
            setBusy(false);
          }}
        >
          {busy ? "Syncing..." : "Sync now"}
        </Button>
      </div>
      <KeyValue
        rows={[
          ["Verified by the server through", state.syncedThrough === 0 ? "nothing yet" : `#${state.syncedThrough}`],
          ["Waiting to upload", behind === 0 ? <Badge tone="good">none</Badge> : <Badge tone="warn">{behind} sale{behind === 1 ? "" : "s"}</Badge>],
          ["Last success", state.lastSuccessMs ? new Date(state.lastSuccessMs).toLocaleString() : "never"],
          ["Server exceptions open", state.openExceptions === 0 ? "none" : <Badge tone="danger">{state.openExceptions}</Badge>],
          ...(pending > 0 ? ([["Fiscal numbers to hand back", `${pending} lease${pending === 1 ? "" : "s"}`]] as [string, React.ReactNode][]) : [])
        ]}
      />
      {state.lastError ? <Notice tone={state.heldAtGap ? "danger" : "warn"}>{state.lastError}</Notice> : null}
    </Card>
  );
}

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
      {s.head ? <SyncCard lastSequence={s.head.lastSequence} /> : null}
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

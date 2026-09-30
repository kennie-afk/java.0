"use client";

import { useState } from "react";
import { Badge, Button, Card, KeyValue, LinkButton, Loading, Notice, PageHeader } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";
import { getHead, readAscending } from "@/lib/journal-store";
import { findings, type Finding, type LocalReport, verifyLocalJournal } from "@/lib/verify-journal";

export default function VerifyPage() {
  const s = useTerminalStatus();
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState(0);
  const [report, setReport] = useState<LocalReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!s.loaded) return <Loading />;
  if (!s.identity) {
    return <Notice tone="warn" title="Nothing to verify">This terminal is not enrolled, so it has no key and no journal.</Notice>;
  }
  const identity = s.identity;

  async function run() {
    setRunning(true);
    setReport(null);
    setError(null);
    setProgress(0);
    try {
      setReport(
        await verifyLocalJournal({
          terminalId: identity.terminalId,
          publicKeySpkiBase64: identity.publicKeySpkiBase64,
          source: { head: getHead, page: readAscending },
          onProgress: setProgress
        })
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "verification failed to run");
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="max-w-3xl space-y-3">
      <PageHeader
        title="Verify journal"
        sub="Re-walks the whole chain from entry 1 in pages: links, sequence, clock order, each sale's content, each digest and each signature."
        actions={<Button variant="primary" onClick={run} disabled={running}>{running ? `Checking... ${progress}` : "Run verification"}</Button>}
      />
      <Notice title="What this can and cannot show">
        The result is a list of findings, never a single pass or fail: a journal can be perfectly chained and still be missing
        a sale. Verification stops at the first problem it finds. It proves the journal is consistent with this terminal&apos;s
        own key. It is not independent proof to anyone else: nothing has been sent to a server, so no second copy exists to
        compare against.
      </Notice>
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {report ? (
        <>
          {findings(report).map((f, i) => (
            <FindingCard key={i} f={f} />
          ))}
          <Card>
            <KeyValue
              rows={[
                ["Entries verified", String(report.entriesChecked)],
                ["Verified through sequence", String(report.verifiedThrough)],
                ["Scan", report.stoppedEarly ? "Stopped at the first problem" : "Reached the end of the journal"]
              ]}
            />
          </Card>
        </>
      ) : null}
      <LinkButton href="/journal">Back to journal</LinkButton>
    </div>
  );
}

function FindingCard({ f }: { f: Finding }) {
  switch (f.kind) {
    case "empty":
      return <Notice title="Journal empty">There are no entries to verify yet.</Notice>;
    case "intact":
      return (
        <Notice tone="good" title="Intact">
          <Badge tone="good">INTACT</Badge> All {f.entries} entries chain correctly from entry 1, in strict sequence, with matching
          content, digests and signatures. No gaps.
        </Notice>
      );
    case "broken":
      return (
        <Notice tone="danger" title={`Broken at sequence ${f.sequence}`}>
          <Badge tone="danger">{f.reason}</Badge> <span>{f.detail}</span>
        </Notice>
      );
    case "gap":
      return (
        <Notice tone="danger" title={`Gap at sequence ${f.fromSequence}${f.toSequence !== f.fromSequence ? ` to ${f.toSequence}` : ""}`}>
          <Badge tone="danger">{f.where === "tail" ? "MISSING AT END" : "MISSING"}</Badge> {f.toSequence - f.fromSequence + 1} sequence number
          {f.toSequence === f.fromSequence ? " is" : "s are"} unaccounted for. A sale that consumed{" "}
          {f.toSequence === f.fromSequence ? "it" : "them"} is not in the journal.
        </Notice>
      );
  }
}

"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Badge, Button, Card, EmptyState, KeyValue, LinkButton, Loading, Notice, PageHeader, Table } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";
import { getEntry } from "@/lib/journal-store";
import { format, money } from "@/lib/money";
import { getSettings } from "@/lib/terminal-store";
import type { JournalRecord } from "@/lib/records";
import { basisPointsToPercent } from "@/lib/rate";

function Receipt() {
  const seq = Number(useSearchParams().get("seq"));
  const s = useTerminalStatus();
  const [rec, setRec] = useState<JournalRecord | null | undefined>(undefined);
  const [shop, setShop] = useState("");

  useEffect(() => {
    void getEntry(seq).then(setRec);
    void getSettings().then((x) => setShop(x.shopName));
  }, [seq]);

  if (!s.loaded || rec === undefined) return <Loading />;
  if (!rec) {
    return (
      <div className="card">
        <EmptyState title="No such journal entry" action={<LinkButton href="/journal">Journal</LinkButton>}>
          There is no entry with sequence {Number.isFinite(seq) ? seq : "(none)"} on this terminal.
        </EmptyState>
      </div>
    );
  }
  const b = rec.sale;
  const fmt = (m: string) => format(money(BigInt(m), b.currency));
  const tendered = b.payments.reduce((n, p) => n + BigInt(p.tenderedMinor), 0n);
  const change = tendered - BigInt(b.totalMinor);
  const when = new Date(rec.epochSecond * 1000 + Math.floor(rec.nano / 1e6));
  const pending = b.fiscal.status === "FISCAL_PENDING";
  const net = b.lines.reduce((n, l) => n + BigInt(l.netMinor), 0n);
  const tax = b.lines.reduce((n, l) => n + BigInt(l.taxMinor), 0n);

  return (
    <div className="max-w-md space-y-3">
      <PageHeader
        title={`Receipt #${rec.sequence}`}
        actions={
          <span className="no-print flex gap-2">
            <Button onClick={() => window.print()}>Print</Button>
            <LinkButton href="/sale" variant="primary">New sale</LinkButton>
          </span>
        }
      />
      <Card className="space-y-3 text-xs">
        <div className="text-center">
          {shop ? <div className="text-lg font-semibold">{shop}</div> : null}
          <div className="text-muted">Terminal {rec.terminalId}</div>
          {b.cashier ? <div className="text-muted">Served by {b.cashier.name} ({b.cashier.staffNumber})</div> : null}
          <div className="text-muted">{when.toLocaleString()}</div>
        </div>
        {pending ? (
          <Notice tone="warn" title="FISCAL_PENDING: no fiscal invoice number yet">
            This sale was recorded and signed offline, but this terminal held no fiscal number lease. It is not a numbered tax
            invoice. It will need a fiscal number when a sync service exists to assign one.
          </Notice>
        ) : (
          <div>
            Fiscal invoice no. <strong className="font-mono">{b.fiscal.number}</strong>
          </div>
        )}
        <Table bare head={[{ label: "Item" }, { label: "Qty", num: true }, { label: "Net", num: true }, { label: "Tax", num: true }]}>
          {b.lines.map((l, i) => (
            <tr key={i}>
              <td>
                {l.name}
                <div className="text-2xs text-muted">@ {fmt(l.unitMinor)}, tax {basisPointsToPercent(l.taxBp)}%</div>
              </td>
              <td className="num">{l.qty}</td>
              <td className="num">{fmt(l.netMinor)}</td>
              <td className="num">{fmt(l.taxMinor)}</td>
            </tr>
          ))}
        </Table>
        <KeyValue
          rows={[
            ["Net", fmt(net.toString())],
            ["Tax", fmt(tax.toString())],
            ["Total", <strong key="t">{fmt(b.totalMinor)}</strong>],
            ...b.payments.map((p, i): [string, React.ReactNode] => [
              `Paid by ${p.method === "CASH" ? "cash" : "mobile money"}${p.reference ? ` (${p.reference}${p.reference.startsWith("MOCK") ? ", simulated" : ""})` : ""}`,
              <span key={i}>{fmt(p.tenderedMinor)}</span>
            ]),
            ["Change", fmt(change.toString())]
          ]}
        />
        <div className="space-y-0.5 border-t border-line pt-2 text-2xs text-muted">
          <div>
            Journal entry {rec.sequence} <Badge tone="info">signed Ed25519</Badge>
          </div>
          <div className="break-all font-mono">digest {rec.digest}</div>
          <div className="break-all font-mono">prev {rec.previousDigest}</div>
        </div>
      </Card>
    </div>
  );
}

export default function ReceiptPage() {
  return (
    <Suspense fallback={<Loading />}>
      <Receipt />
    </Suspense>
  );
}

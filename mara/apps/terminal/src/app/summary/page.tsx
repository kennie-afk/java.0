"use client";

import { useEffect, useState } from "react";
import { Card, EmptyState, LinkButton, Loading, Notice, PageHeader, SectionTitle, Table } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";
import { format, money } from "@/lib/money";
import { summariseJournal, type Summary } from "@/lib/summary";

export default function SummaryPage() {
  const s = useTerminalStatus();
  const [sum, setSum] = useState<Summary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void summariseJournal().then(setSum).catch((e) => setError(e instanceof Error ? e.message : "could not read the journal"));
  }, [s.head?.lastSequence]);

  if (!s.loaded || (!sum && !error)) return <Loading />;
  const fmt = (m: bigint) => format(money(m, s.currency));

  return (
    <div className="space-y-3">
      <PageHeader
        title="Sales summary"
        sub="Read straight from this terminal's signed journal, so it always agrees with it. Per terminal: there is no shared back office yet."
        actions={<LinkButton href="/journal/verify">Verify chain</LinkButton>}
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {sum && sum.entries === 0 ? (
        <div className="card">
          <EmptyState title="No sales yet">Complete a sale and it appears here.</EmptyState>
        </div>
      ) : null}
      {sum && sum.entries > 0 ? (
        <>
          <Table
            head={[
              { label: "Day" }, { label: "Sales", num: true }, { label: "Net", num: true }, { label: "Tax", num: true },
              { label: "Total", num: true }, { label: "Cash", num: true }, { label: "Mobile", num: true }, { label: "Fiscal pending", num: true }
            ]}
          >
            {sum.days.map((d) => (
              <tr key={d.day}>
                <td className="font-medium">{d.day}</td>
                <td className="num">{d.sales}</td>
                <td className="num">{fmt(d.netMinor)}</td>
                <td className="num">{fmt(d.taxMinor)}</td>
                <td className="num font-medium">{fmt(d.grossMinor)}</td>
                <td className="num">{fmt(d.cashMinor)}</td>
                <td className="num">{fmt(d.mobileMinor)}</td>
                <td className="num">{d.pending}</td>
              </tr>
            ))}
          </Table>
          <div className="grid gap-3 md:grid-cols-2">
            <Card>
              <SectionTitle>Top items (by net sales)</SectionTitle>
              <Table bare head={[{ label: "Item" }, { label: "Qty", num: true }, { label: "Net", num: true }]}>
                {sum.items.map((i) => (
                  <tr key={i.name}>
                    <td>{i.name}</td>
                    <td className="num">{i.qty}</td>
                    <td className="num">{fmt(i.netMinor)}</td>
                  </tr>
                ))}
              </Table>
            </Card>
            <Card>
              <SectionTitle>By cashier</SectionTitle>
              <Table bare head={[{ label: "Cashier" }, { label: "Sales", num: true }, { label: "Total", num: true }]}>
                {sum.cashiers.map((c) => (
                  <tr key={c.name}>
                    <td>{c.name}</td>
                    <td className="num">{c.sales}</td>
                    <td className="num">{fmt(c.grossMinor)}</td>
                  </tr>
                ))}
              </Table>
            </Card>
          </div>
        </>
      ) : null}
    </div>
  );
}

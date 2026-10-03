"use client";

import { useState } from "react";
import { Card, Empty, Field, Input, Notice, PageHeader, Select } from "@/components/ui";
import { useOp } from "@/components/use-op";
import { addDays, isoDay, money } from "@/lib/format";
import type { Branch, SalesRow, Staff, Terminal } from "@/lib/types";

export default function Sales() {
  const branches = useOp<Branch[]>("branches");
  const zone = branches.data?.[0]?.timezone ?? "Africa/Nairobi";
  const [from, setFrom] = useState(() => addDays(isoDay(new Date(), "Africa/Nairobi"), -6));
  const [to, setTo] = useState(() => isoDay(new Date(), "Africa/Nairobi"));
  const [by, setBy] = useState("day");
  const report = useOp<SalesRow[]>("report.sales", { from, to, by, zone }, !!branches.data);
  const terminals = useOp<Terminal[]>("terminals");
  const staff = useOp<Staff[]>("staff");
  const terminalName = (id?: string) => terminals.data?.find((t) => t.id === id)?.label ?? id ?? "";
  const cashierName = (id?: string) => (id ? staff.data?.find((s) => s.id === id)?.display_name ?? id : "(not recorded)");
  const rows = report.data ?? [];
  const sum = (f: (r: SalesRow) => number) => rows.reduce((n, r) => n + f(r), 0);

  return (
    <div className="space-y-3">
      <PageHeader title="Sales" sub="Cash and mobile money are what was applied to each sale. Days are in the shop's own time." />
      <Card>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
          <Field label="From"><Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
          <Field label="To"><Input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
          <Field label="Group by">
            <Select value={by} onChange={(e) => setBy(e.target.value)}>
              <option value="day">Day</option><option value="terminal">Day and till</option><option value="cashier">Day and cashier</option>
            </Select>
          </Field>
        </div>
      </Card>
      {report.error ? <Notice tone="danger">{report.error}</Notice> : null}
      <Card className="p-0">
        {rows.length === 0 && !report.loading ? <Empty>No sales in that range.</Empty> : (
          <table className="tbl">
            <thead><tr>
              <th>Day</th>{by === "terminal" ? <th>Till</th> : null}{by === "cashier" ? <th>Cashier</th> : null}
              <th className="num">Sales</th><th className="num">Total</th><th className="num">Cash</th><th className="num">Mobile money</th><th className="num">Not yet numbered</th>
            </tr></thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={i}>
                  <td>{r.day}</td>{by === "terminal" ? <td>{terminalName(r.terminalId)}</td> : null}{by === "cashier" ? <td>{cashierName(r.cashierStaffId)}</td> : null}
                  <td className="num">{r.sales}</td><td className="num">{money(r.totalMinor)}</td><td className="num">{money(r.cashMinor)}</td>
                  <td className="num">{money(r.mobileMinor)}</td><td className="num">{r.fiscalPending}</td>
                </tr>
              ))}
              <tr className="font-semibold">
                <td>Total</td>{by !== "day" ? <td /> : null}
                <td className="num">{sum((r) => r.sales)}</td><td className="num">{money(sum((r) => r.totalMinor))}</td>
                <td className="num">{money(sum((r) => r.cashMinor))}</td><td className="num">{money(sum((r) => r.mobileMinor))}</td><td className="num">{sum((r) => r.fiscalPending)}</td>
              </tr>
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}

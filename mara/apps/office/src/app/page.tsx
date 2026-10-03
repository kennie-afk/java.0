"use client";

import { Card, Empty, Notice, PageHeader, SectionTitle, Stat } from "@/components/ui";
import { useOp } from "@/components/use-op";
import { addDays, isoDay, money } from "@/lib/format";
import type { Branch, CoreException, SalesRow, SyncException } from "@/lib/types";

export default function Today() {
  const branches = useOp<Branch[]>("branches");
  const zone = branches.data?.[0]?.timezone ?? "Africa/Nairobi";
  const today = isoDay(new Date(), zone);
  const report = useOp<SalesRow[]>("report.sales", { from: addDays(today, -6), to: today, by: "day", zone }, !!branches.data);
  const coreExc = useOp<CoreException[]>("core.exceptions", { open: "true", limit: "100" });
  const syncExc = useOp<SyncException[]>("sync.exceptions", { open: "true", limit: "100" });

  const rows = report.data ?? [];
  const day = (d: string) => rows.find((r) => r.day === d);
  const t = day(today);
  const y = day(addDays(today, -1));
  const open = (coreExc.data?.length ?? 0) + (syncExc.data?.length ?? 0);
  const error = branches.error ?? report.error;

  return (
    <div className="space-y-3">
      <PageHeader title="Today" sub={`${today} · all tills`} />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {open > 0 ? (
        <Notice tone="warn" title={`${open} thing${open === 1 ? "" : "s"} need${open === 1 ? "s" : ""} a look`}>
          The servers found sales that did not add up or entries that did not verify. See Exceptions.
        </Notice>
      ) : null}
      <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
        <Stat label="Sales today" value={String(t?.sales ?? 0)} sub={y ? `${y.sales} yesterday` : undefined} />
        <Stat label="Takings today" value={money(t?.totalMinor ?? 0)} sub={y ? `${money(y.totalMinor)} yesterday` : undefined} />
        <Stat label="Cash" value={money(t?.cashMinor ?? 0)} />
        <Stat label="Mobile money" value={money(t?.mobileMinor ?? 0)} />
      </div>
      {t && t.fiscalPending > 0 ? (
        <Notice tone="info">{t.fiscalPending} of today's sales are waiting for a fiscal number. They will be numbered when the till next reaches the server.</Notice>
      ) : null}
      <Card className="p-0">
        <div className="p-3 pb-0"><SectionTitle>Last 7 days</SectionTitle></div>
        {rows.length === 0 && !report.loading ? <Empty>No sales in the last 7 days yet.</Empty> : (
          <table className="tbl">
            <thead><tr><th>Day</th><th className="num">Sales</th><th className="num">Total</th><th className="num">Cash</th><th className="num">Mobile money</th><th className="num">Tax</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.day}>
                  <td>{r.day}</td><td className="num">{r.sales}</td><td className="num">{money(r.totalMinor)}</td>
                  <td className="num">{money(r.cashMinor)}</td><td className="num">{money(r.mobileMinor)}</td><td className="num">{money(r.taxMinor)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}

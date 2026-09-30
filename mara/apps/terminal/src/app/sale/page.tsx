"use client";

import Link from "next/link";
import { EmptyState, LinkButton, Loading, Notice, PageHeader, Pager, Table, usePagedList } from "@/components/ui";
import { RequireStaff } from "@/components/require-staff";
import { useTerminalStatus } from "@/components/use-status";
import { format, money, plus } from "@/lib/money";
import { priceCart } from "@/lib/sale";
import { tabLinesToCart } from "@/lib/record-sale";
import { listTabs, type TabCursor } from "@/lib/tab-store";

function SalePageInner() {
  const s = useTerminalStatus();
  const list = usePagedList((after: TabCursor | null) => listTabs({ after, limit: 20 }), []);
  if (!s.loaded || (!list.page && !list.error)) return <Loading />;

  return (
    <div className="space-y-3">
      <PageHeader
        title="Sale"
        sub="A tab holds a customer's items until it is settled. A checkout is a tab opened and closed in one visit."
        actions={s.identity ? <LinkButton href="/sale/new" variant="primary">New tab</LinkButton> : undefined}
      />
      {!s.identity ? (
        <Notice tone="warn" title="This terminal is not enrolled">
          Sales are signed with the key created at enrolment and carry the terminal id identity-service issued, so a device
          cannot sell before it has both. <Link href="/enrol" className="underline">Enrol this terminal</Link>. Selling
          itself will then work with no network.
        </Notice>
      ) : null}
      {list.page && list.page.items.length === 0 ? (
        <div className="card">
          <EmptyState title="No open tabs" action={s.identity ? <LinkButton href="/sale/new" variant="primary">Open a tab</LinkButton> : undefined}>
            Open a tab, add items from the catalogue, then take payment.
          </EmptyState>
        </div>
      ) : (
        <Table head={[{ label: "Tab" }, { label: "Opened" }, { label: "Lines", num: true }, { label: "Total", num: true }, { label: "" }]}>
          {list.page?.items.map((t) => {
            const totals = priceCart(s.currency, tabLinesToCart(t.lines));
            return (
              <tr key={t.id}>
                <td className="font-medium">{t.label || "Unnamed tab"}</td>
                <td>{new Date(t.openedAt).toLocaleString()}</td>
                <td className="num">{t.lines.length}</td>
                <td className="num">{format(plus(money(0n, s.currency), totals.total))}</td>
                <td className="num">
                  <Link href={`/sale/tab?id=${encodeURIComponent(t.id)}`} className="text-accent">
                    Open
                  </Link>
                </td>
              </tr>
            );
          })}
        </Table>
      )}
      <Pager shown={list.page?.items.length ?? 0} hasPrev={list.hasPrev} hasNext={list.hasNext} onPrev={list.prev} onNext={list.next} noun="tabs" />
    </div>
  );
}

export default function SalePage() {
  return (
    <RequireStaff>
      <SalePageInner />
    </RequireStaff>
  );
}

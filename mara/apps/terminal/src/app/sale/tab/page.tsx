"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";
import { Button, Card, ConfirmDelete, EmptyState, Input, LinkButton, Loading, Notice, PageHeader, Pager, SectionTitle, Table, usePagedList } from "@/components/ui";
import { RequireStaff } from "@/components/require-staff";
import { useTerminalStatus } from "@/components/use-status";
import { type CatalogueCursor, listItems } from "@/lib/catalogue-store";
import { format, money } from "@/lib/money";
import { tabLinesToCart } from "@/lib/record-sale";
import type { Tab } from "@/lib/records";
import { priceCart } from "@/lib/sale";
import { addToTab, discardTab, getTab, setQuantity } from "@/lib/tab-store";

function TabView() {
  const id = useSearchParams().get("id") ?? "";
  const router = useRouter();
  const s = useTerminalStatus();
  const [tab, setTab] = useState<Tab | null | undefined>(undefined);
  const [q, setQ] = useState("");
  const [error, setError] = useState<string | null>(null);
  const list = usePagedList((after: CatalogueCursor | null) => listItems({ q, after, limit: 12 }), [q]);

  useEffect(() => {
    void getTab(id).then(setTab);
  }, [id]);

  const run = useCallback(async (fn: () => Promise<Tab>) => {
    try {
      setTab(await fn());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not update the tab");
    }
  }, []);

  if (!s.loaded || tab === undefined) return <Loading />;
  if (!tab) {
    return (
      <div className="card">
        <EmptyState title="This tab is closed" action={<LinkButton href="/sale">Back to tabs</LinkButton>}>
          It was settled or discarded.
        </EmptyState>
      </div>
    );
  }

  const totals = priceCart(s.currency, tabLinesToCart(tab.lines));
  const fmt = (m: bigint) => format(money(m, s.currency));

  return (
    <div className="space-y-3">
      <PageHeader
        title={tab.label || "Unnamed tab"}
        sub="Prices exclude tax; tax is added per line."
        actions={
          <ConfirmDelete
            label="Discard tab"
            what="this tab and everything on it"
            onConfirm={async () => {
              await discardTab(tab.id);
              router.push("/sale");
            }}
          />
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      <div className="grid gap-3 lg:grid-cols-2">
        <div>
          <SectionTitle>Add from catalogue</SectionTitle>
          <div className="mb-2 max-w-xs">
            <Input placeholder="Name prefix, or scan an exact SKU" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search catalogue" />
          </div>
          {list.page && list.page.items.length === 0 ? (
            <div className="card">
              <EmptyState title={q ? "No match" : "The catalogue is empty"} action={q ? undefined : <LinkButton href="/catalogue/new" variant="primary">Add an item</LinkButton>}>
                {q ? "Nothing starts with that name or has that SKU." : "Add products before selling them."}
              </EmptyState>
            </div>
          ) : (
            <Table head={[{ label: "Item" }, { label: "Price", num: true }, { label: "" }]}>
              {list.page?.items.map((i) => (
                <tr key={i.id}>
                  <td className="font-medium">{i.name}</td>
                  <td className="num">{fmt(BigInt(i.unitMinor))}</td>
                  <td className="num">
                    <Button onClick={() => run(() => addToTab(tab.id, i))}>Add</Button>
                  </td>
                </tr>
              ))}
            </Table>
          )}
          <Pager shown={list.page?.items.length ?? 0} hasPrev={list.hasPrev} hasNext={list.hasNext} onPrev={list.prev} onNext={list.next} noun="items" />
        </div>

        <div>
          <SectionTitle>On this tab</SectionTitle>
          {tab.lines.length === 0 ? (
            <div className="card">
              <EmptyState title="Nothing on the tab yet">Add items from the catalogue.</EmptyState>
            </div>
          ) : (
            <Table head={[{ label: "Item" }, { label: "Qty", num: true }, { label: "Net", num: true }, { label: "Tax", num: true }, { label: "" }]}>
              {totals.lines.map((l, idx) => (
                <tr key={tab.lines[idx].itemId}>
                  <td className="font-medium">{l.name}</td>
                  <td className="num">{l.qty}</td>
                  <td className="num">{fmt(l.netMinor)}</td>
                  <td className="num">{fmt(l.taxMinor)}</td>
                  <td className="num whitespace-nowrap">
                    <Button aria-label={`One fewer ${l.name}`} onClick={() => run(() => setQuantity(tab.id, tab.lines[idx].itemId, l.qty - 1))}>-</Button>{" "}
                    <Button aria-label={`One more ${l.name}`} onClick={() => run(() => setQuantity(tab.id, tab.lines[idx].itemId, l.qty + 1))}>+</Button>
                  </td>
                </tr>
              ))}
            </Table>
          )}
          <Card className="mt-2 space-y-1 text-xs">
            <div className="flex justify-between"><span className="text-muted">Net</span><span className="tabular-nums">{fmt(totals.net.minor)}</span></div>
            <div className="flex justify-between"><span className="text-muted">Tax</span><span className="tabular-nums">{fmt(totals.tax.minor)}</span></div>
            <div className="flex justify-between text-base font-semibold"><span>Total</span><span className="tabular-nums">{fmt(totals.total.minor)}</span></div>
          </Card>
          <div className="mt-2 flex gap-2">
            {s.identity && tab.lines.length > 0 ? (
              <LinkButton href={`/sale/tender?id=${encodeURIComponent(tab.id)}`} variant="primary">Take payment</LinkButton>
            ) : (
              <Button variant="primary" disabled>Take payment</Button>
            )}
            <LinkButton href="/sale">All tabs</LinkButton>
          </div>
          {!s.identity ? <p className="mt-2 text-xs text-warn">This terminal is not enrolled, so it cannot record a sale.</p> : null}
        </div>
      </div>
    </div>
  );
}

export default function TabPage() {
  return (
    <Suspense fallback={<Loading />}>
      <RequireStaff><TabView /></RequireStaff>
    </Suspense>
  );
}

"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui";
import { loadDemoCatalogue } from "@/lib/demo-catalogue";
import { EmptyState, Input, LinkButton, Loading, Notice, PageHeader, Pager, Table, usePagedList } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";
import { type CatalogueCursor, listItems } from "@/lib/catalogue-store";
import { format, money } from "@/lib/money";
import { basisPointsToPercent } from "@/lib/rate";

export default function CataloguePage() {
  const [reload, setReload] = useState(0);
  const s = useTerminalStatus(reload);
  const [q, setQ] = useState("");
  const [loadNote, setLoadNote] = useState<string | null>(null);
  const list = usePagedList((after: CatalogueCursor | null) => listItems({ q, after, limit: 25 }), [q, reload]);

  if (!s.loaded || (!list.page && !list.error)) return <Loading />;

  return (
    <div className="space-y-3">
      <PageHeader
        title="Catalogue"
        sub={`${s.items} item${s.items === 1 ? "" : "s"} on this terminal. Kept only in this browser; there is no shared catalogue yet.`}
        actions={
          <span className="flex gap-2">
            <Button
              onClick={async () => {
                const r = await loadDemoCatalogue();
                setLoadNote(`Demo catalogue: ${r.added} added, ${r.skipped} already present. Prices and tax rates are illustrative.`);
                setReload((n) => n + 1);
              }}
            >
              Load demo items
            </Button>
            <LinkButton href="/catalogue/new" variant="primary">Add item</LinkButton>
          </span>
        }
      />
      {loadNote ? <Notice tone="info">{loadNote}</Notice> : null}
      {list.error ? <Notice tone="danger">{list.error}</Notice> : null}
      {s.items === 0 && q === "" ? (
        <div className="card">
          <EmptyState title="The catalogue is empty" action={<LinkButton href="/catalogue/new" variant="primary">Add the first item</LinkButton>}>
            Nothing is pre-loaded. Add the products this counter sells, with their price and tax rate.
          </EmptyState>
        </div>
      ) : (
        <>
          <div className="max-w-xs">
            <Input placeholder="Search by name prefix or exact SKU" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search catalogue" />
          </div>
          {list.page && list.page.items.length === 0 ? (
            <div className="card">
              <EmptyState title="No match">No item starts with that name or has that SKU.</EmptyState>
            </div>
          ) : (
            <Table head={[{ label: "Name" }, { label: "SKU" }, { label: "Price (ex tax)", num: true }, { label: "Tax", num: true }, { label: "" }]}>
              {list.page?.items.map((i) => (
                <tr key={i.id}>
                  <td className="font-medium">{i.name}</td>
                  <td className="font-mono text-muted">{i.sku}</td>
                  <td className="num">{format(money(BigInt(i.unitMinor), s.currency))}</td>
                  <td className="num">{basisPointsToPercent(i.taxBp)}%</td>
                  <td className="num">
                    <Link href={`/catalogue/edit?id=${encodeURIComponent(i.id)}`} className="text-accent">
                      Edit
                    </Link>
                  </td>
                </tr>
              ))}
            </Table>
          )}
          <Pager
            shown={list.page?.items.length ?? 0}
            hasPrev={list.hasPrev}
            hasNext={list.hasNext}
            onPrev={list.prev}
            onNext={list.next}
            noun="items"
          />
        </>
      )}
    </div>
  );
}

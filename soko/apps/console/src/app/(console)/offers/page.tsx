import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { Pager, SearchBar, listQuery, parsePaging, type PagingQuery } from "@/components/pager";
import { Badge, Notice, PageHeader, Table, buttonClass, rowClass } from "@/components/ui";
import type { OfferRow } from "@/lib/types";

export default async function OffersPage({ searchParams }: { searchParams: Promise<PagingQuery> }) {
  const { q, page } = parsePaging(await searchParams);
  let offers: OfferRow[] = [];
  let total = 0;
  let hasMore = false;
  let error: string | null = null;

  try {
    ({ items: offers, total, hasMore } = await api.page<OfferRow>(`/v1/offers?${listQuery(q, page)}`));
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="Offers" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader title="Offers"
        subtitle="What each supplier will sell, at what price, and how much they can cover."
        actions={<Link href="/offers/new" className={buttonClass}>Add offer</Link>} />
      <SearchBar q={q} placeholder="Search by product or supplier" />
      <Table head={["Supplier", "Product", "Cost", "List", "Spread", "Available", "Handling"]}>
        {offers.map((offer) => {
          const spread = (offer.listPriceCents ?? 0) - offer.costCents;
          return (
            <tr key={offer.id} className={rowClass}>
              <td className="px-4 py-3 font-medium">{offer.supplier}</td>
              <td className="px-4 py-3">{offer.product}</td>
              <td className="px-4 py-3 tabular-nums text-[var(--color-muted)]">{ksh(offer.costCents)}</td>
              <td className="px-4 py-3 tabular-nums">{offer.listPriceCents ? ksh(offer.listPriceCents) : "—"}</td>
              <td className="px-4 py-3 tabular-nums text-[var(--color-good)]">{ksh(spread)}</td>
              <td className="px-4 py-3 tabular-nums">{offer.availableQty}</td>
              <td className="px-4 py-3">
                {offer.coldChain ? <Badge value="Chilled" /> : <Badge value="Ambient" />}
              </td>
            </tr>
          );
        })}
      </Table>
      <Pager q={q} page={page} shown={offers.length} total={total} hasMore={hasMore} />
    </>
  );
}

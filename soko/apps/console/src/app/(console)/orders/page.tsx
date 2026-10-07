import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { Pager, SearchBar, listQuery, parsePaging, type PagingQuery } from "@/components/pager";
import { Badge, Notice, PageHeader, Table, buttonClass, rowClass } from "@/components/ui";
import type { OrderRow } from "@/lib/types";

export default async function OrdersPage({ searchParams }: { searchParams: Promise<PagingQuery> }) {
  const { q, page } = parsePaging(await searchParams);
  let orders: OrderRow[] = [];
  let total = 0;
  let hasMore = false;
  let error: string | null = null;

  try {
    ({ items: orders, total, hasMore } = await api.page<OrderRow>(`/v1/orders?${listQuery(q, page)}`));
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="Orders" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader title="Orders" subtitle="Every order, the buyer it came from and the spread it earned."
        actions={<Link href="/orders/new" className={buttonClass}>New order</Link>} />
      <SearchBar q={q} placeholder="Search by reference or customer" />
      <Table head={["Reference", "Customer", "County", "Revenue", "Cost", "Margin", "Status"]}>
        {orders.map((order) => (
          <tr key={order.id} className={rowClass}>
            <td className="px-4 py-3">
              <Link href={`/orders/${order.id}`} className="font-medium hover:underline">{order.reference}</Link>
            </td>
            <td className="px-4 py-3">{order.customer}</td>
            <td className="px-4 py-3 text-[var(--color-muted)]">{order.county}</td>
            <td className="px-4 py-3 tabular-nums">{ksh(order.revenueCents)}</td>
            <td className="px-4 py-3 tabular-nums text-[var(--color-muted)]">{ksh(order.costCents)}</td>
            <td className="px-4 py-3 tabular-nums text-[var(--color-good)]">{ksh(order.marginCents)}</td>
            <td className="px-4 py-3"><Badge value={order.status} /></td>
          </tr>
        ))}
      </Table>
      <Pager q={q} page={page} shown={orders.length} total={total} hasMore={hasMore} />
    </>
  );
}

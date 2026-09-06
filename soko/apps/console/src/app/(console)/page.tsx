import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { Card, Notice, PageHeader, Stat, Table, rowClass, secondaryButtonClass } from "@/components/ui";
import type { Overview, OrderRow } from "@/lib/types";

export default async function OverviewPage() {
  let data: Overview | null = null;
  let orders: OrderRow[] = [];
  let error: string | null = null;

  try {
    data = await api.get<Overview>("/v1/overview");
    orders = await api.get<OrderRow[]>("/v1/orders?limit=6");
  } catch (caught) {
    error = describeError(caught);
  }

  if (error || !data) {
    return (
      <>
        <PageHeader title="Overview" />
        <Notice tone="danger">{error}</Notice>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Overview"
        subtitle="What was ordered, which supplier it was routed to, and what the spread earned."
        actions={<Link href="/orders" className={secondaryButtonClass}>All orders</Link>}
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Orders" value={String(data.orders)} hint="routed to a supplier" />
        <Stat label="Revenue" value={ksh(data.revenueCents)} hint="at list price" />
        <Stat label="Margin" value={ksh(data.marginCents)} hint="list price less supplier cost" tone="good" />
        <Stat label="Margin rate" value={`${data.marginPercent}%`} hint="across every order" />
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <Card title="Network" description="Who supplies, what is sold, who buys.">
          <dl className="space-y-2.5 text-[0.8125rem]">
            <div className="flex justify-between"><dt className="text-[var(--color-muted)]">Suppliers</dt><dd className="font-medium tabular-nums">{data.suppliers}</dd></div>
            <div className="flex justify-between"><dt className="text-[var(--color-muted)]">Products</dt><dd className="font-medium tabular-nums">{data.products}</dd></div>
            <div className="flex justify-between"><dt className="text-[var(--color-muted)]">Customers</dt><dd className="font-medium tabular-nums">{data.customers}</dd></div>
          </dl>
          <p className="mt-4 text-[0.75rem] leading-relaxed text-[var(--color-faint)]">
            Stock is never held. Each line is routed to the cheapest supplier that can keep the cold
            chain and still arrive with shelf life left.
          </p>
        </Card>

        <Card title="Latest orders" description="Newest first.">
          {orders.length === 0 ? (
            <p className="text-[0.8125rem] text-[var(--color-muted)]">No orders yet.</p>
          ) : (
            <div className="space-y-2.5">
              {orders.map((order) => (
                <Link key={order.id} href={`/orders/${order.id}`}
                  className="flex items-baseline justify-between rounded-md px-2 py-1.5 text-[0.8125rem] transition-colors hover:bg-[var(--color-raised)]">
                  <span className="font-medium">{order.reference}</span>
                  <span className="text-[var(--color-muted)]">{order.customer}</span>
                  <span className="tabular-nums text-[var(--color-good)]">{ksh(order.marginCents)}</span>
                </Link>
              ))}
            </div>
          )}
        </Card>
      </div>
    </>
  );
}

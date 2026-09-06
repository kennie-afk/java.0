import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { Badge, Notice, PageHeader, Table, rowClass } from "@/components/ui";
import type { OrderRow } from "@/lib/types";

export default async function OrdersPage() {
  let orders: OrderRow[] = [];
  let error: string | null = null;

  try {
    orders = await api.get<OrderRow[]>("/v1/orders?limit=100");
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="Orders" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader title="Orders" subtitle="Every order, the buyer it came from and the spread it earned." />
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
    </>
  );
}

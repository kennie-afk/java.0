import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { Card, Notice, PageHeader, Stat, Table, rowClass, secondaryButtonClass } from "@/components/ui";

interface Line {
  product: string | null;
  supplier: string | null;
  quantity: number;
  unitPriceCents: number;
  unitCostCents: number;
  marginCents: number;
  routingReason: string | null;
}

interface OrderDetail {
  id: string;
  reference: string;
  status: string;
  revenueCents: number;
  costCents: number;
  marginCents: number;
  lines: Line[];
}

export default async function OrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  let order: OrderDetail | null = null;
  let error: string | null = null;

  try {
    order = await api.get<OrderDetail>(`/v1/orders/${id}`);
  } catch (caught) {
    error = describeError(caught);
  }

  if (error || !order) {
    return (<><PageHeader title="Order" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader title={order.reference} subtitle="Each line, and why it went to the supplier it did."
        actions={
          <span className="flex gap-2">
            <Link href={`/orders/${order.id}/receipt`} className={secondaryButtonClass}>Receipt</Link>
            <Link href="/orders" className={secondaryButtonClass}>All orders</Link>
          </span>
        } />

      <div className="grid gap-3 sm:grid-cols-3">
        <Stat label="Revenue" value={ksh(order.revenueCents)} />
        <Stat label="Supplier cost" value={ksh(order.costCents)} />
        <Stat label="Margin" value={ksh(order.marginCents)} tone="good" />
      </div>

      <div className="mt-6">
        <Table head={["Product", "Routed to", "Qty", "Sell", "Cost", "Margin"]}>
          {order.lines.map((line, index) => (
            <tr key={index} className={rowClass}>
              <td className="px-4 py-3 font-medium">{line.product}</td>
              <td className="px-4 py-3">{line.supplier}</td>
              <td className="px-4 py-3 tabular-nums">{line.quantity}</td>
              <td className="px-4 py-3 tabular-nums">{ksh(line.unitPriceCents)}</td>
              <td className="px-4 py-3 tabular-nums text-[var(--color-muted)]">{ksh(line.unitCostCents)}</td>
              <td className="px-4 py-3 tabular-nums text-[var(--color-good)]">{ksh(line.marginCents)}</td>
            </tr>
          ))}
        </Table>
      </div>

      <p className="mt-4 text-[0.75rem] leading-relaxed text-[var(--color-faint)]">
        {order.lines[0]?.routingReason ??
          "Routing picks the cheapest supplier that can hold the cold chain and beat the shelf life."}
      </p>
    </>
  );
}

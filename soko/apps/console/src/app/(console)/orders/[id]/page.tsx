import Link from "next/link";
import { ActionForm } from "@/components/action-form";
import { cancelOrder } from "../../actions";
import { dangerButtonClass, inputClass } from "@/components/ui";
import { api, describeError, ksh } from "@/lib/api";
import { Badge, Card, Notice, PageHeader, Stat, Table, rowClass, secondaryButtonClass } from "@/components/ui";

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
  cancelReason?: string | null;
  revenueCents: number;
  costCents: number;
  marginCents: number;
  lines: Line[];
}

export default async function OrderPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ notice?: string }> }) {
  const { id } = await params;
  const { notice } = await searchParams;
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
          <span className="flex items-center gap-2">
            <Badge value={order.status} />
            <Link href={`/orders/${order.id}/receipt`} className={secondaryButtonClass}>Receipt</Link>
            <Link href="/orders" className={secondaryButtonClass}>All orders</Link>
          </span>
        } />

      <div className="grid gap-3 sm:grid-cols-3">
        <Stat label="Revenue" value={ksh(order.revenueCents)} />
        <Stat label="Supplier cost" value={ksh(order.costCents)} />
        <Stat label="Margin" value={ksh(order.marginCents)} tone="good" />
      </div>

      {notice === "cancelled" ? (
        <div className="mt-4"><Notice tone="good">Order cancelled. Stock is back on the offers and the commission is voided.</Notice></div>
      ) : null}

      {order.status === "CANCELLED" ? (
        <div className="mt-4"><Notice tone="warn">Cancelled{order.cancelReason ? `: ${order.cancelReason}` : ""}. Stock went back on the offers and the commission was voided.</Notice></div>
      ) : null}

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

      {order.status === "ROUTED" ? (
        <details className="mt-6 max-w-md rounded-md border border-[var(--color-line)] bg-[var(--color-surface)] p-3">
          <summary className="cursor-pointer text-[0.958rem] font-medium">Cancel this order…</summary>
          <ActionForm action={cancelOrder} submit="Confirm cancellation" pending="Cancelling…" button={dangerButtonClass} className="mt-3 space-y-3">
            <input type="hidden" name="orderId" value={order.id} />
            <input name="reason" placeholder="Reason (optional)" className={inputClass} />
            <p className="text-[0.833rem] text-[var(--color-muted)]">Only an order that has not been paid can be cancelled. Its stock is returned.</p>
          </ActionForm>
        </details>
      ) : null}

      <p className="mt-4 text-[0.875rem] leading-relaxed text-[var(--color-faint)]">
        {order.lines[0]?.routingReason ??
          "Routing picks the cheapest supplier that can hold the cold chain and beat the shelf life."}
      </p>
    </>
  );
}

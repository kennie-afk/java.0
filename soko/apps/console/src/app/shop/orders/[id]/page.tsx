import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { PayPanel } from "@/components/pay-panel";
import { Badge, Notice, PageHeader, Table, rowClass, secondaryButtonClass } from "@/components/ui";

interface Line {
  product: string;
  quantity: number;
  unitPriceCents: number;
  lineTotalCents: number;
  status: string;
  dispatchedAt: string | null;
  deliveredAt: string | null;
}

interface Detail {
  reference: string;
  status: string;
  totalCents: number;
  placedAt: string;
  lines: Line[];
}

export default async function MyOrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  let order: Detail | null = null;
  let error: string | null = null;

  try {
    order = await api.get<Detail>(`/v1/shop/orders/${id}`);
  } catch (caught) {
    error = describeError(caught);
  }

  if (error || !order) {
    return (<><PageHeader title="Order" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader
        title={order.reference}
        subtitle={`Placed ${new Date(order.placedAt).toLocaleDateString("en-KE", { dateStyle: "long" })}`}
        actions={
          <div className="flex gap-2">
            <Link href={`/shop/orders/${id}/receipt`} className={secondaryButtonClass}>Receipt</Link>
            <Link href="/shop/orders" className={secondaryButtonClass}>My orders</Link>
          </div>
        }
      />
      {order.status === "ROUTED" || order.status === "PAID" ? (
        <div className="mb-6 max-w-xl"><PayPanel orderId={id} totalCents={order.totalCents} /></div>
      ) : null}
      <Table head={["Item", "Qty", "Unit", "Total", "Progress"]}>
        {order.lines.map((line, index) => (
          <tr key={index} className={rowClass}>
            <td className="px-4 py-3 font-medium">{line.product}</td>
            <td className="px-4 py-3 tabular-nums">{line.quantity}</td>
            <td className="px-4 py-3 tabular-nums">{ksh(line.unitPriceCents)}</td>
            <td className="px-4 py-3 tabular-nums">{ksh(line.lineTotalCents)}</td>
            <td className="px-4 py-3">
              <Badge value={line.status} />
              {(line.dispatchedAt || line.deliveredAt) && (
                <p className="mt-1 text-[0.875rem] text-[var(--color-faint)]">
                  {line.deliveredAt
                    ? `Delivered ${new Date(line.deliveredAt).toLocaleDateString("en-KE", { dateStyle: "medium" })}`
                    : `Dispatched ${new Date(line.dispatchedAt as string).toLocaleDateString("en-KE", { dateStyle: "medium" })}`}
                </p>
              )}
            </td>
          </tr>
        ))}
      </Table>
      <div className="mt-4 flex justify-end text-[1.083rem] font-semibold tabular-nums">
        Total {ksh(order.totalCents)}
      </div>
    </>
  );
}

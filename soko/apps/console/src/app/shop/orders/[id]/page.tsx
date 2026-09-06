import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
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
        actions={<Link href="/shop/orders" className={secondaryButtonClass}>My orders</Link>}
      />
      <Table head={["Item", "Qty", "Unit", "Total", "Progress"]}>
        {order.lines.map((line, index) => (
          <tr key={index} className={rowClass}>
            <td className="px-4 py-3 font-medium">{line.product}</td>
            <td className="px-4 py-3 tabular-nums">{line.quantity}</td>
            <td className="px-4 py-3 tabular-nums">{ksh(line.unitPriceCents)}</td>
            <td className="px-4 py-3 tabular-nums">{ksh(line.lineTotalCents)}</td>
            <td className="px-4 py-3"><Badge value={line.status} /></td>
          </tr>
        ))}
      </Table>
      <div className="mt-4 flex justify-end text-[0.9375rem] font-semibold tabular-nums">
        Total {ksh(order.totalCents)}
      </div>
    </>
  );
}

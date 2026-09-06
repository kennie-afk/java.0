import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { Badge, Notice, PageHeader, Table, rowClass } from "@/components/ui";

interface Row {
  id: string;
  reference: string;
  status: string;
  totalCents: number;
  placedAt: string;
}

export default async function MyOrdersPage() {
  let rows: Row[] = [];
  let error: string | null = null;

  try {
    rows = await api.get<Row[]>("/v1/shop/orders");
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="My orders" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader title="My orders" subtitle="Everything you have bought, newest first." />
      <Table head={["Reference", "Placed", "Total", "Status"]}>
        {rows.map((row) => (
          <tr key={row.id} className={rowClass}>
            <td className="px-4 py-3">
              <Link href={`/shop/orders/${row.id}`} className="font-medium hover:underline">
                {row.reference}
              </Link>
            </td>
            <td className="px-4 py-3 text-[var(--color-muted)]">
              {new Date(row.placedAt).toLocaleDateString("en-KE", { dateStyle: "medium" })}
            </td>
            <td className="px-4 py-3 tabular-nums">{ksh(row.totalCents)}</td>
            <td className="px-4 py-3"><Badge value={row.status} /></td>
          </tr>
        ))}
      </Table>
    </>
  );
}

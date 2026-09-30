import Link from "next/link";
import { api, describeError } from "@/lib/api";
import { Card, EmptyState, Notice, PageHeader, Table, buttonClass, rowClass } from "@/components/ui";

interface Customer { id: string; name: string; phone: string; county: string }

export default async function CustomersPage() {
  let customers: Customer[] = [];
  let error: string | null = null;
  try {
    customers = await api.get<Customer[]>("/v1/customers?limit=200");
  } catch (caught) {
    error = describeError(caught);
  }
  if (error) return (<><PageHeader title="Customers" /><Notice tone="danger">{error}</Notice></>);
  return (
    <>
      <PageHeader title="Customers" subtitle="The shops and buyers you sell to."
        actions={<Link href="/customers/new" className={buttonClass}>Add customer</Link>} />
      <Card>
        {customers.length === 0 ? <EmptyState message="No customers yet" detail="Add one to place an order on their behalf." /> : (
          <Table head={["Customer", "Phone", "County"]}>
            {customers.map((c) => (
              <tr key={c.id} className={rowClass}>
                <td className="px-3.5 py-2.5 font-medium">{c.name}</td>
                <td className="px-3.5 py-2.5 tabular-nums text-[var(--color-muted)]">{c.phone}</td>
                <td className="px-3.5 py-2.5 text-[var(--color-muted)]">{c.county}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </>
  );
}

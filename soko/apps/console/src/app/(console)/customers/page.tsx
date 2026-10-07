import Link from "next/link";
import { api, describeError } from "@/lib/api";
import { Pager, SearchBar, listQuery, parsePaging, type PagingQuery } from "@/components/pager";
import { Card, EmptyState, Notice, PageHeader, Table, buttonClass, rowClass } from "@/components/ui";

interface Customer { id: string; name: string; phone: string; county: string }

export default async function CustomersPage({ searchParams }: { searchParams: Promise<PagingQuery> }) {
  const { q, page } = parsePaging(await searchParams);
  let customers: Customer[] = [];
  let total = 0;
  let hasMore = false;
  let error: string | null = null;
  try {
    ({ items: customers, total, hasMore } = await api.page<Customer>(`/v1/customers?${listQuery(q, page)}`));
  } catch (caught) {
    error = describeError(caught);
  }
  if (error) return (<><PageHeader title="Customers" /><Notice tone="danger">{error}</Notice></>);
  return (
    <>
      <PageHeader title="Customers" subtitle="The shops and buyers you sell to."
        actions={<Link href="/customers/new" className={buttonClass}>Add customer</Link>} />
      <SearchBar q={q} placeholder="Search by name, phone or county" />
      <Card>
        {customers.length === 0 && !q ? <EmptyState message="No customers yet" detail="Add one to place an order on their behalf." /> : (
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
      <Pager q={q} page={page} shown={customers.length} total={total} hasMore={hasMore} />
    </>
  );
}

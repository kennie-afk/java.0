import Link from "next/link";
import { api, describeError } from "@/lib/api";
import { Pager, SearchBar, listQuery, parsePaging, type PagingQuery } from "@/components/pager";
import { Badge, Notice, PageHeader, Table, buttonClass, rowClass } from "@/components/ui";
import type { SupplierRow } from "@/lib/types";

export default async function SuppliersPage({ searchParams }: { searchParams: Promise<PagingQuery> }) {
  const { q, page } = parsePaging(await searchParams);
  let suppliers: SupplierRow[] = [];
  let total = 0;
  let hasMore = false;
  let error: string | null = null;

  try {
    ({ items: suppliers, total, hasMore } = await api.page<SupplierRow>(`/v1/suppliers?${listQuery(q, page)}`));
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="Suppliers" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader title="Suppliers"
        subtitle="Lead time and cold chain decide what each supplier is allowed to fulfil."
        actions={<Link href="/suppliers/new" className={buttonClass}>Add supplier</Link>} />
      <SearchBar q={q} placeholder="Search by name or county" />
      <Table head={["Supplier", "County", "Lead time", "Cold chain", "Reliability", "Status", ""]}>
        {suppliers.map((supplier) => (
          <tr key={supplier.id} className={rowClass}>
            <td className="px-4 py-3 font-medium">{supplier.name}</td>
            <td className="px-4 py-3 text-[var(--color-muted)]">{supplier.county}</td>
            <td className="px-4 py-3 tabular-nums">{supplier.leadTimeHours} h</td>
            <td className="px-4 py-3">
              {supplier.coldChain ? <Badge value="Chilled" /> : <Badge value="Ambient" />}
            </td>
            <td className="px-4 py-3 tabular-nums">{Math.round(Number(supplier.reliability) * 100)}%</td>
            <td className="px-4 py-3"><Badge value={supplier.status} /></td>
            <td className="px-4 py-3 text-right">
              <Link href={`/suppliers/${supplier.id}`} className="text-[0.958rem] font-medium hover:underline">Edit</Link>
            </td>
          </tr>
        ))}
      </Table>
      <Pager q={q} page={page} shown={suppliers.length} total={total} hasMore={hasMore} />
    </>
  );
}

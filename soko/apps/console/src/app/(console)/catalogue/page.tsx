import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { Pager, SearchBar, listQuery, parsePaging, type PagingQuery } from "@/components/pager";
import { Badge, Notice, PageHeader, Table, buttonClass, rowClass } from "@/components/ui";
import type { ProductRow } from "@/lib/types";

function shelfLife(hours: number): string {
  if (hours < 48) return `${hours} h`;
  return `${Math.round(hours / 24)} days`;
}

export default async function CataloguePage({ searchParams }: { searchParams: Promise<PagingQuery> }) {
  const { q, page } = parsePaging(await searchParams);
  let products: ProductRow[] = [];
  let total = 0;
  let hasMore = false;
  let error: string | null = null;

  try {
    ({ items: products, total, hasMore } = await api.page<ProductRow>(`/v1/products?${listQuery(q, page)}`));
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="Catalogue" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader title="Catalogue"
        subtitle="What is sold, and the handling each item demands. Shelf life and cold chain drive routing."
        actions={<Link href="/catalogue/new" className={buttonClass}>Add product</Link>} />
      <SearchBar q={q} placeholder="Search by name, SKU or category" />
      <Table head={["SKU", "Product", "Category", "Unit", "Shelf life", "Handling", "List price", ""]}>
        {products.map((product) => (
          <tr key={product.id} className={rowClass}>
            <td className="px-4 py-3 font-mono text-[0.875rem] text-[var(--color-muted)]">{product.sku}</td>
            <td className="px-4 py-3 font-medium">{product.name}</td>
            <td className="px-4 py-3 text-[var(--color-muted)]">{product.category}</td>
            <td className="px-4 py-3 text-[var(--color-muted)]">{product.unit}</td>
            <td className="px-4 py-3 tabular-nums">{shelfLife(product.shelfLifeHours)}</td>
            <td className="px-4 py-3">
              <span className="flex flex-wrap gap-1.5">
                {product.requiresColdChain ? <Badge value="Chilled" /> : null}
                {product.perishable ? <Badge value="Perishable" /> : <Badge value="Ambient" />}
              </span>
            </td>
            <td className="px-4 py-3 tabular-nums">{ksh(product.listPriceCents)}</td>
            <td className="px-4 py-3 text-right">
              <Link href={`/catalogue/${product.id}`} className="text-[0.958rem] font-medium hover:underline">Edit</Link>
            </td>
          </tr>
        ))}
      </Table>
      <Pager q={q} page={page} shown={products.length} total={total} hasMore={hasMore} />
    </>
  );
}

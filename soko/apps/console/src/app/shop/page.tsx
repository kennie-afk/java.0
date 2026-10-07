import { api, describeError } from "@/lib/api";
import { PAGE_SIZE, listQuery, parsePaging, type PagingQuery } from "@/components/pager";
import { Notice, PageHeader } from "@/components/ui";
import { Storefront } from "@/components/storefront";

export interface ShopProduct {
  id: string;
  sku: string;
  name: string;
  category: string;
  unit: string;
  perishable: boolean;
  chilled: boolean;
  shelfLifeHours: number;
  priceCents: number;
  inStock: number;
}

export default async function ShopPage({ searchParams }: { searchParams: Promise<PagingQuery> }) {
  const { q, page } = parsePaging(await searchParams);
  let products: ShopProduct[] = [];
  let total = 0;
  let hasMore = false;
  let error: string | null = null;

  try {
    ({ items: products, total, hasMore } = await api.page<ShopProduct>(`/v1/shop/products?${listQuery(q, page)}`));
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="Shop" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader
        title="Fresh today"
        subtitle="Delivered direct from the farm that has it. Chilled items travel under cold chain."
      />
      <Storefront products={products} q={q} page={page} pageSize={PAGE_SIZE} matches={total} hasMore={hasMore} />
    </>
  );
}

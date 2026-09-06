import { api, describeError } from "@/lib/api";
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

export default async function ShopPage() {
  let products: ShopProduct[] = [];
  let error: string | null = null;

  try {
    products = await api.get<ShopProduct[]>("/v1/shop/products");
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
      <Storefront products={products} />
    </>
  );
}

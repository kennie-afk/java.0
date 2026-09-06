import { api, describeError, ksh } from "@/lib/api";
import { Badge, Notice, PageHeader, Table, rowClass } from "@/components/ui";
import type { ProductRow } from "@/lib/types";

function shelfLife(hours: number): string {
  if (hours < 48) return `${hours} h`;
  return `${Math.round(hours / 24)} days`;
}

export default async function CataloguePage() {
  let products: ProductRow[] = [];
  let error: string | null = null;

  try {
    products = await api.get<ProductRow[]>("/v1/products");
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="Catalogue" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader title="Catalogue"
        subtitle="What is sold, and the handling each item demands. Shelf life and cold chain drive routing." />
      <Table head={["SKU", "Product", "Category", "Unit", "Shelf life", "Handling", "List price"]}>
        {products.map((product) => (
          <tr key={product.id} className={rowClass}>
            <td className="px-4 py-3 font-mono text-[0.75rem] text-[var(--color-muted)]">{product.sku}</td>
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
          </tr>
        ))}
      </Table>
    </>
  );
}

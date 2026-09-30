import Link from "next/link";
import { api, describeError } from "@/lib/api";
import { ActionForm } from "@/components/action-form";
import { Card, Notice, PageHeader, Select, inputClass, secondaryButtonClass } from "@/components/ui";
import type { ProductRow } from "@/lib/types";
import { placeOrder } from "../../actions";

interface Customer { id: string; name: string; county: string }

export default async function NewOrderPage() {
  let customers: Customer[] = [];
  let products: ProductRow[] = [];
  let error: string | null = null;
  try {
    customers = await api.get<Customer[]>("/v1/customers?limit=200");
    products = await api.get<ProductRow[]>("/v1/products");
  } catch (caught) {
    error = describeError(caught);
  }
  const productOptions = products.map((p) => ({
    value: p.id,
    label: `${p.name} · ${p.unit} · KSh ${(p.listPriceCents / 100).toLocaleString("en-KE")}`
  }));
  return (
    <>
      <PageHeader title="New order" subtitle="Each line goes to the cheapest supplier that can keep the cold chain and deliver inside the shelf life. A line nobody can fill is refused with the reason."
        actions={<Link href="/orders" className={secondaryButtonClass}>Back</Link>} />
      {error ? <Notice tone="danger">{error}</Notice> : (
        <div className="max-w-2xl">
          <Card>
            <ActionForm action={placeOrder} submit="Place order" pending="Routing…">
              <Select name="customerId" label="Customer" placeholder="Choose a customer…"
                options={customers.map((c) => ({ value: c.id, label: `${c.name} · ${c.county}` }))} />
              <div className="space-y-2">
                <p className="text-[0.958rem] font-medium">Lines</p>
                {[0, 1, 2, 3, 4].map((i) => (
                  <div key={i} className="grid grid-cols-[1fr_6rem] gap-2">
                    <Select name={`product${i}`} placeholder={i === 0 ? "Choose a product…" : "Add another product…"} options={productOptions} aria-label={`Product ${i + 1}`} />
                    <input name={`quantity${i}`} type="number" min="1" placeholder="Qty" aria-label={`Quantity ${i + 1}`} className={`${inputClass} mt-2`} />
                  </div>
                ))}
              </div>
            </ActionForm>
          </Card>
        </div>
      )}
    </>
  );
}

import Link from "next/link";
import { ActionForm } from "@/components/action-form";
import { api, describeError } from "@/lib/api";
import { Card, Field, Notice, PageHeader, inputClass, secondaryButtonClass } from "@/components/ui";
import type { ProductRow } from "@/lib/types";
import { updateProduct } from "../../actions";

interface Detail extends ProductRow { photoUrl?: string | null }

export default async function EditProductPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  let product: Detail | null = null;
  let error: string | null = null;
  try {
    product = await api.get<Detail>(`/v1/products/${id}`);
  } catch (caught) {
    error = describeError(caught);
  }
  if (error || !product) {
    return (<><PageHeader title="Product" /><Notice tone="danger">{error}</Notice></>);
  }
  return (
    <>
      <PageHeader title={product.name}
        subtitle={`SKU ${product.sku}, sold per ${product.unit}. These cannot change; add a new product instead. A new price applies to orders placed from now on.`}
        actions={<Link href="/catalogue" className={secondaryButtonClass}>Back</Link>} />
      <div className="max-w-lg">
        <Card>
          <ActionForm action={updateProduct} submit="Save changes" pending="Saving…">
            <input type="hidden" name="id" value={product.id} />
            <Field label="Name"><input name="name" defaultValue={product.name} className={inputClass} required maxLength={200} /></Field>
            <Field label="Category"><input name="category" defaultValue={product.category} className={inputClass} required maxLength={60} /></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="List price (KSh)">
                <input name="price" inputMode="decimal" defaultValue={(product.listPriceCents / 100).toFixed(2)} className={inputClass} required />
              </Field>
              <Field label="Shelf life (hours)">
                <input name="shelfLifeHours" type="number" min="1" step="1" defaultValue={product.shelfLifeHours} className={inputClass} required />
              </Field>
            </div>
            <Field label="Photo address" hint="Optional.">
              <input name="photoUrl" type="url" defaultValue={product.photoUrl ?? ""} className={inputClass} maxLength={500} />
            </Field>
          </ActionForm>
        </Card>
      </div>
    </>
  );
}

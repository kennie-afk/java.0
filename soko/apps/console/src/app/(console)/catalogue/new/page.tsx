import Link from "next/link";
import { ActionForm } from "@/components/action-form";
import { Card, Field, PageHeader, inputClass, secondaryButtonClass } from "@/components/ui";
import { createProduct } from "../../actions";

export default function NewProductPage() {
  return (
    <>
      <PageHeader title="Add product" subtitle="Shelf life and cold chain are what routing checks against each supplier."
        actions={<Link href="/catalogue" className={secondaryButtonClass}>Back</Link>} />
      <div className="max-w-lg">
        <Card>
          <ActionForm action={createProduct} submit="Add product" pending="Adding…">
            <Field label="SKU" hint="Unique in your catalogue.">
              <input name="sku" className={inputClass} placeholder="MLK-500" required maxLength={60} />
            </Field>
            <Field label="Name"><input name="name" className={inputClass} placeholder="Fresh milk" required maxLength={200} /></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Category"><input name="category" className={inputClass} placeholder="Dairy" required maxLength={60} /></Field>
              <Field label="Sold per"><input name="unit" className={inputClass} placeholder="500 ml" required maxLength={20} /></Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="List price (KSh)">
                <input name="price" inputMode="decimal" className={inputClass} placeholder="120.00" required />
              </Field>
              <Field label="Shelf life (hours)">
                <input name="shelfLifeHours" type="number" min="1" step="1" className={inputClass} placeholder="48" required />
              </Field>
            </div>
            <Field label="Photo address" hint="Optional.">
              <input name="photoUrl" type="url" className={inputClass} placeholder="https://" maxLength={500} />
            </Field>
            <label className="flex items-center gap-2 text-[0.958rem]">
              <input type="checkbox" name="perishable" /> Perishable: refuse suppliers whose lead time meets the shelf life
            </label>
            <label className="flex items-center gap-2 text-[0.958rem]">
              <input type="checkbox" name="requiresColdChain" /> Needs the cold chain
            </label>
          </ActionForm>
        </Card>
      </div>
    </>
  );
}

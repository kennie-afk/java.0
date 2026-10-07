import Link from "next/link";
import { ActionForm } from "@/components/action-form";
import { LookupPicker } from "@/components/lookup-picker";
import { Card, Field, PageHeader, inputClass, secondaryButtonClass } from "@/components/ui";
import { createOffer } from "../../actions";

export default function NewOfferPage() {
  return (
    <>
      <PageHeader title="Add offer" subtitle="One offer per supplier and product. Routing picks the cheapest viable one for each order line."
        actions={<Link href="/offers" className={secondaryButtonClass}>Back</Link>} />
      <div className="max-w-lg">
        <Card>
          <ActionForm action={createOffer} submit="Add offer" pending="Adding…">
            <Field label="Supplier"><LookupPicker kind="suppliers" name="supplierId" label="Supplier" placeholder="Search suppliers…" /></Field>
            <Field label="Product"><LookupPicker kind="products" name="productId" label="Product" placeholder="Search products…" /></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Cost to you (KSh)">
                <input name="cost" inputMode="decimal" className={inputClass} placeholder="80.00" required />
              </Field>
              <Field label="Available">
                <input name="availableQty" type="number" min="0" step="1" className={inputClass} placeholder="100" required />
              </Field>
            </div>
          </ActionForm>
        </Card>
      </div>
    </>
  );
}

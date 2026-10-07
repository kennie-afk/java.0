import Link from "next/link";
import { ActionForm } from "@/components/action-form";
import { Card, Field, PageHeader, inputClass, secondaryButtonClass } from "@/components/ui";
import { createSupplier } from "../../actions";

export default function NewSupplierPage() {
  return (
    <>
      <PageHeader title="Add supplier" subtitle="Lead time and cold chain decide which products routing will let this supplier fulfil."
        actions={<Link href="/suppliers" className={secondaryButtonClass}>Back</Link>} />
      <div className="max-w-lg">
        <Card>
          <ActionForm action={createSupplier} submit="Add supplier" pending="Adding…">
            <Field label="Name"><input name="name" className={inputClass} placeholder="Limuru Dairy Co-op" required maxLength={200} /></Field>
            <Field label="County"><input name="county" className={inputClass} placeholder="Kiambu" required maxLength={100} /></Field>
            <Field label="Lead time (hours)" hint="From order to delivery. A perishable product is refused when this meets its shelf life.">
              <input name="leadTimeHours" type="number" min="1" step="1" className={inputClass} placeholder="24" required />
            </Field>
            <Field label="Reliability (%)" hint="Optional. Breaks ties between equally priced suppliers. Defaults to 90.">
              <input name="reliability" type="number" min="0" max="100" step="0.1" className={inputClass} placeholder="90" />
            </Field>
            <label className="flex items-center gap-2 text-[0.958rem]">
              <input type="checkbox" name="coldChain" /> Keeps the cold chain
            </label>
          </ActionForm>
        </Card>
      </div>
    </>
  );
}

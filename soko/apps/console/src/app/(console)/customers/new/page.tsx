import Link from "next/link";
import { ActionForm } from "@/components/action-form";
import { Card, Field, PageHeader, inputClass, secondaryButtonClass } from "@/components/ui";
import { createCustomer } from "../../actions";

export default function NewCustomerPage() {
  return (
    <>
      <PageHeader title="Add customer" actions={<Link href="/customers" className={secondaryButtonClass}>Back</Link>} />
      <div className="max-w-lg">
        <Card>
          <ActionForm action={createCustomer} submit="Add customer" pending="Adding…">
            <Field label="Name"><input name="name" className={inputClass} placeholder="Naivas Thika Road" /></Field>
            <Field label="Phone" hint="Used for order notifications."><input name="phone" className={inputClass} placeholder="+254 700 000 000" /></Field>
            <Field label="County"><input name="county" className={inputClass} placeholder="Kiambu" /></Field>
          </ActionForm>
        </Card>
      </div>
    </>
  );
}

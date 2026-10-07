import Link from "next/link";
import { ActionForm } from "@/components/action-form";
import { api, describeError } from "@/lib/api";
import { Card, Field, Notice, PageHeader, Select, inputClass, secondaryButtonClass } from "@/components/ui";
import type { SupplierRow } from "@/lib/types";
import { updateSupplier } from "../../actions";

export default async function EditSupplierPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  let supplier: SupplierRow | null = null;
  let error: string | null = null;
  try {
    supplier = await api.get<SupplierRow>(`/v1/suppliers/${id}`);
  } catch (caught) {
    error = describeError(caught);
  }
  if (error || !supplier) {
    return (<><PageHeader title="Supplier" /><Notice tone="danger">{error}</Notice></>);
  }
  return (
    <>
      <PageHeader title={supplier.name} subtitle="Making a supplier inactive stops routing to it; orders already routed are unaffected."
        actions={<Link href="/suppliers" className={secondaryButtonClass}>Back</Link>} />
      <div className="max-w-lg">
        <Card>
          <ActionForm action={updateSupplier} submit="Save changes" pending="Saving…">
            <input type="hidden" name="id" value={supplier.id} />
            <Field label="Name"><input name="name" defaultValue={supplier.name} className={inputClass} required maxLength={200} /></Field>
            <Field label="County"><input name="county" defaultValue={supplier.county} className={inputClass} required maxLength={100} /></Field>
            <Field label="Lead time (hours)">
              <input name="leadTimeHours" type="number" min="1" step="1" defaultValue={supplier.leadTimeHours} className={inputClass} required />
            </Field>
            <Field label="Reliability (%)">
              <input name="reliability" type="number" min="0" max="100" step="0.1"
                defaultValue={Math.round(Number(supplier.reliability) * 1000) / 10} className={inputClass} required />
            </Field>
            <Select name="status" label="Status" defaultValue={supplier.status} placeholder="Choose…"
              options={[{ value: "ACTIVE", label: "Active" }, { value: "INACTIVE", label: "Inactive" }]} />
            <label className="flex items-center gap-2 text-[0.958rem]">
              <input type="checkbox" name="coldChain" defaultChecked={supplier.coldChain} /> Keeps the cold chain
            </label>
          </ActionForm>
        </Card>
      </div>
    </>
  );
}

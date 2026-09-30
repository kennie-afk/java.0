import Link from "next/link";
import { api, describeError } from "@/lib/api";
import { ActionForm } from "@/components/action-form";
import { Card, Field, Notice, PageHeader, Select, inputClass, secondaryButtonClass } from "@/components/ui";
import type { OfferRow } from "@/lib/types";
import { recordWastage } from "../../actions";

export default async function NewWastagePage() {
  let offers: OfferRow[] = [];
  let error: string | null = null;
  try {
    offers = await api.get<OfferRow[]>("/v1/offers?limit=200");
  } catch (caught) {
    error = describeError(caught);
  }
  return (
    <>
      <PageHeader title="Record wastage" subtitle="Choose the offer the spoiled stock came from."
        actions={<Link href="/wastage" className={secondaryButtonClass}>Back</Link>} />
      {error ? <Notice tone="danger">{error}</Notice> : (
        <div className="max-w-lg">
          <Card>
            <ActionForm action={recordWastage} submit="Record wastage" pending="Recording…">
              <Select name="offerId" label="Offer" placeholder="Choose an offer…"
                options={offers.map((o) => ({ value: o.id, label: `${o.product} · ${o.supplier} (${o.availableQty} in stock)` }))} />
              <Field label="Quantity"><input name="quantity" type="number" min="1" step="1" className={inputClass} /></Field>
              <Select name="reason" label="Reason" placeholder="Reason" defaultValue="EXPIRED"
                options={[{ value: "EXPIRED", label: "Expired" }, { value: "SPOILED", label: "Spoiled in transit" }, { value: "DAMAGED", label: "Damaged" }]} />
            </ActionForm>
          </Card>
        </div>
      )}
    </>
  );
}

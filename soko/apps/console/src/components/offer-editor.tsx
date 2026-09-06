"use client";

import { useActionState } from "react";
import { updateOffer, type PortalState } from "@/app/supplier/actions";
import { Badge, buttonClass, inputClass } from "@/components/ui";

const INITIAL: PortalState = { error: null, message: null };

export interface SupplierOffer {
  id: string;
  product: string;
  sku: string;
  unit: string;
  category: string;
  costCents: number;
  availableQty: number;
  status: string;
  listPriceCents: number;
  chilled: boolean;
  shelfLifeHours: number;
}

export function OfferEditor({ offer }: { offer: SupplierOffer }) {
  const [state, action, saving] = useActionState(updateOffer, INITIAL);

  return (
    <form action={action}
      className="flex flex-wrap items-end gap-4 rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] p-4">
      <input type="hidden" name="offerId" value={offer.id} />

      <div className="min-w-[200px] flex-1">
        <p className="text-[0.9375rem] font-medium">{offer.product}</p>
        <p className="mt-0.5 text-[0.75rem] text-[var(--color-muted)]">
          per {offer.unit} · {offer.sku}
          {offer.chilled ? " · needs cold chain" : ""}
        </p>
      </div>

      <label className="block">
        <span className="text-[0.6875rem] uppercase tracking-wide text-[var(--color-faint)]">
          Your price
        </span>
        <input name="cost" type="number" step="0.01" min="0.01"
          defaultValue={(offer.costCents / 100).toFixed(2)}
          className={`${inputClass} mt-1 w-32`} />
      </label>

      <label className="block">
        <span className="text-[0.6875rem] uppercase tracking-wide text-[var(--color-faint)]">
          Available
        </span>
        <input name="availableQty" type="number" min="0" defaultValue={offer.availableQty}
          className={`${inputClass} mt-1 w-28`} />
      </label>

      <button type="submit" className={buttonClass} disabled={saving}>
        {saving ? "Saving…" : "Save"}
      </button>

      <Badge value={offer.status} />

      {state.error ? (
        <p className="w-full text-[0.75rem] text-[var(--color-danger)]">{state.error}</p>
      ) : state.message ? (
        <p className="w-full text-[0.75rem] text-[var(--color-good)]">{state.message}</p>
      ) : null}
    </form>
  );
}

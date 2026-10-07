"use client";

import { newKey } from "@/lib/idempotency";
import { useState } from "react";
import { ActionForm } from "@/components/action-form";
import { LookupPicker } from "@/components/lookup-picker";
import { Field, inputClass } from "@/components/ui";
import { placeOrder } from "@/app/(console)/actions";

/**
 * New-order form. Customer and products are searched on the server as you type, so the 51st
 * product is as reachable as the first. One idempotency key lives as long as the form does: a
 * second click, or a resend after a timeout, returns the first order rather than placing another.
 */
export function OrderForm() {
  const [key] = useState(() => newKey());
  return (
    <ActionForm action={placeOrder} submit="Place order" pending="Routing…">
      <input type="hidden" name="idempotencyKey" value={key} />
      <Field label="Customer">
        <LookupPicker kind="customers" name="customerId" label="Customer" placeholder="Search customers…" />
      </Field>
      <div className="space-y-2">
        <p className="text-[0.958rem] font-medium">Lines</p>
        {[0, 1, 2, 3, 4].map((i) => (
          <div key={i} className="grid grid-cols-[1fr_6rem] items-start gap-2">
            <LookupPicker kind="products" name={`product${i}`} label={`Product ${i + 1}`}
              placeholder={i === 0 ? "Search products…" : "Add another product…"} />
            <input name={`quantity${i}`} type="number" min="1" step="1" placeholder="Qty"
              aria-label={`Quantity ${i + 1}`} className={inputClass} />
          </div>
        ))}
      </div>
    </ActionForm>
  );
}

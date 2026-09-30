"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button, Card, Field, Input, LinkButton, Notice } from "@/components/ui";
import { createItem, DuplicateSkuError, updateItem } from "@/lib/catalogue-store";
import { format, money, parseMajor } from "@/lib/money";
import { basisPointsToPercent, percentToBasisPoints } from "@/lib/rate";
import type { CatalogueItem } from "@/lib/records";

/** Shared by the new and edit routes. Prices are typed in major units and parsed exactly. */
export function CatalogueForm({ currency, item }: { currency: string; item?: CatalogueItem }) {
  const router = useRouter();
  const [sku, setSku] = useState(item?.sku ?? "");
  const [name, setName] = useState(item?.name ?? "");
  const [price, setPrice] = useState(item ? majorText(item.unitMinor, currency) : "");
  const [rate, setRate] = useState(item ? basisPointsToPercent(item.taxBp) : "16");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const minor = parseMajor(price, currency);
  const bp = percentToBasisPoints(rate);
  const errors = {
    sku: sku.trim() === "" ? "Required." : null,
    name: name.trim() === "" ? "Required." : null,
    price: minor === null ? "Enter an amount like 150 or 150.50." : minor === 0n ? "Must be more than zero." : null,
    rate: bp === null ? "Enter 0 to 100, up to two decimals." : null
  };
  const invalid = Object.values(errors).some(Boolean);

  return (
    <form
      noValidate
      className="max-w-xl space-y-3"
      onSubmit={async (e) => {
        e.preventDefault();
        if (invalid || minor === null || bp === null) return;
        setBusy(true);
        setError(null);
        try {
          const input = { sku, name, unitMinor: minor, taxBp: bp };
          if (item) await updateItem(item.id, input);
          else await createItem(input);
          router.push("/catalogue");
        } catch (err) {
          setError(err instanceof DuplicateSkuError ? err.message : err instanceof Error ? err.message : "Could not save.");
          setBusy(false);
        }
      }}
    >
      <Card className="space-y-3">
        <Field label="SKU or barcode" error={sku !== "" ? errors.sku : null} hint="Unique on this terminal.">
          <Input value={sku} maxLength={40} onChange={(e) => setSku(e.target.value)} autoComplete="off" />
        </Field>
        <Field label="Name" error={name !== "" ? errors.name : null}>
          <Input value={name} maxLength={80} onChange={(e) => setName(e.target.value)} autoComplete="off" />
        </Field>
        <Field
          label={`Unit price, ${currency}, before tax`}
          error={price !== "" ? errors.price : null}
          hint="Prices on this terminal exclude tax; tax is added at the rate below."
        >
          <Input value={price} inputMode="decimal" onChange={(e) => setPrice(e.target.value)} autoComplete="off" />
        </Field>
        <Field label="Tax rate (%)" error={errors.rate} hint="16 is Kenya's standard VAT rate; use 0 for zero-rated or exempt items.">
          <Input value={rate} inputMode="decimal" onChange={(e) => setRate(e.target.value)} autoComplete="off" />
        </Field>
        {minor !== null && bp !== null && minor > 0n ? (
          <p className="text-xs text-muted">
            Shown to the customer as {format(money(minor, currency))} plus {basisPointsToPercent(bp)}% tax.
          </p>
        ) : null}
      </Card>
      {error ? <Notice tone="danger">{error}</Notice> : null}
      <div className="flex gap-2">
        <Button variant="primary" type="submit" disabled={invalid || busy}>
          {item ? "Save changes" : "Add item"}
        </Button>
        <LinkButton href="/catalogue">Cancel</LinkButton>
      </div>
    </form>
  );
}

function majorText(unitMinor: string, currency: string): string {
  const text = format(money(BigInt(unitMinor), currency));
  return text.slice(currency.length + 1);
}

"use client";

import { useEffect, useState } from "react";
import { Button, Card, Field, Input, Loading, Notice, PageHeader, Select } from "@/components/ui";
import { CURRENCY_DIGITS } from "@/lib/money";
import { countEntries } from "@/lib/journal-store";
import { getSettings, saveSettings } from "@/lib/terminal-store";

export default function SettingsPage() {
  const [currency, setCurrency] = useState("KES");
  const [shopName, setShopName] = useState("");
  const [entries, setEntries] = useState<number | null>(null);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      const s = await getSettings();
      setCurrency(s.currency);
      setShopName(s.shopName);
      setEntries(await countEntries());
    })();
  }, []);

  if (entries === null) return <Loading />;
  const locked = entries > 0;

  return (
    <div className="max-w-xl space-y-3">
      <PageHeader title="Settings" sub="Terminal-local. These are not synchronised anywhere." />
      <form
        className="space-y-3"
        onSubmit={async (e) => {
          e.preventDefault();
          setError(null);
          try {
            await saveSettings({ currency, shopName: shopName.trim() });
            setSaved(true);
          } catch (err) {
            setError(err instanceof Error ? err.message : "could not save");
          }
        }}
      >
        <Card className="space-y-3">
          <Field
            label="Currency"
            hint={locked ? "Locked: the journal already holds sales in this currency." : "Cannot be changed once the first sale is recorded."}
          >
            <Select
              value={currency}
              disabled={locked}
              onChange={(e) => {
                setCurrency(e.target.value);
                setSaved(false);
              }}
            >
              {Object.entries(CURRENCY_DIGITS).map(([code, digits]) => (
                <option key={code} value={code}>
                  {code} ({digits} decimal places)
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Shop name for receipts" hint="Printed on receipts. Not part of the signed sale record.">
            <Input value={shopName} maxLength={80} onChange={(e) => { setShopName(e.target.value); setSaved(false); }} />
          </Field>
          <Button variant="primary" type="submit">
            Save
          </Button>
        </Card>
      </form>
      {saved ? <Notice tone="good">Saved on this device.</Notice> : null}
      {error ? <Notice tone="danger">{error}</Notice> : null}
    </div>
  );
}

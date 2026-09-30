"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { Button, Card, EmptyState, Field, Input, LinkButton, Loading, Notice, PageHeader, SectionTitle, Select } from "@/components/ui";
import { RequireStaff } from "@/components/require-staff";
import { useTerminalStatus } from "@/components/use-status";
import { useStaffSession } from "@/components/use-session";
import { format, money, parseMajor } from "@/lib/money";
import { NotEnrolledError, recordSale, SaleRefusedError, tabLinesToCart } from "@/lib/record-sale";
import type { Tab } from "@/lib/records";
import { applyTender, type PayMethod, priceCart, splitEvenly } from "@/lib/sale";
import { getTab } from "@/lib/tab-store";

interface Row {
  key: number;
  method: PayMethod;
  amount: string;
  reference: string;
}

let rowKey = 1;

function Tender() {
  const id = useSearchParams().get("id") ?? "";
  const router = useRouter();
  const s = useTerminalStatus();
  const { session } = useStaffSession();
  const [tab, setTab] = useState<Tab | null | undefined>(undefined);
  const [rows, setRows] = useState<Row[]>([{ key: 0, method: "CASH", amount: "", reference: "" }]);
  const [people, setPeople] = useState("2");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [phones, setPhones] = useState<Record<number, string>>({});
  const [prompting, setPrompting] = useState<number | null>(null);
  const [promptNote, setPromptNote] = useState<Record<number, string>>({});

  useEffect(() => {
    void getTab(id).then(setTab);
  }, [id]);

  const cur = s.currency;
  const totals = useMemo(() => (tab ? priceCart(cur, tabLinesToCart(tab.lines)) : null), [tab, cur]);

  if (!s.loaded || tab === undefined) return <Loading />;
  if (!tab || !totals || tab.lines.length === 0) {
    return (
      <div className="card">
        <EmptyState title="Nothing to settle" action={<LinkButton href="/sale">Back to tabs</LinkButton>}>
          This tab is closed or empty.
        </EmptyState>
      </div>
    );
  }
  if (!s.identity) {
    return <Notice tone="warn" title="This terminal is not enrolled">A sale cannot be signed without the terminal key.</Notice>;
  }

  const fmt = (m: bigint) => format(money(m, cur));
  const parsed = rows.map((r) => ({ row: r, minor: parseMajor(r.amount, cur) }));
  const allParsed = parsed.every((p) => p.minor !== null && p.minor > 0n);
  const result = allParsed
    ? applyTender(totals.total, parsed.map((p) => ({ method: p.row.method, tenderedMinor: p.minor as bigint, reference: p.row.reference })))
    : null;
  const tendered = parsed.reduce((n, p) => n + (p.minor ?? 0n), 0n);
  const outstanding = totals.total.minor - tendered;

  const update = (key: number, patch: Partial<Row>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));

  function split() {
    const n = Number(people);
    if (!Number.isInteger(n) || n < 1 || n > 50) return;
    const shares = splitEvenly(totals!.total, n);
    setRows(shares.map((m) => ({ key: rowKey++, method: "CASH" as PayMethod, amount: format(m).slice(cur.length + 1), reference: "" })));
  }

  /** Asks the SIMULATED M-Pesa service for a prompt and fills in the receipt it returns. */
  async function requestMobile(key: number, minor: bigint | null) {
    if (minor === null || minor <= 0n) {
      setPromptNote((n) => ({ ...n, [key]: "Enter the amount first." }));
      return;
    }
    setPrompting(key);
    setPromptNote((n) => ({ ...n, [key]: "Waiting for the customer..." }));
    try {
      const r = await fetch("/api/mpesa-mock", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ phone: phones[key] ?? "", amountMinor: minor.toString() })
      });
      const j = (await r.json()) as { status?: string; receipt?: string; message?: string };
      if (r.ok && j.status === "SUCCESS" && j.receipt) {
        update(key, { reference: `MOCK-${j.receipt}` });
        setPromptNote((n) => ({ ...n, [key]: `Simulated payment accepted (${j.receipt}). No money moved.` }));
      } else {
        setPromptNote((n) => ({ ...n, [key]: j.message ?? "The prompt was not completed." }));
      }
    } catch {
      setPromptNote((n) => ({ ...n, [key]: "Could not reach this terminal's server." }));
    } finally {
      setPrompting(null);
    }
  }

  async function settle() {
    if (!result?.ok) return;
    setBusy(true);
    setError(null);
    try {
      const entry = await recordSale({
        tabId: tab!.id,
        cashier: session ? { staffId: session.staffId, staffNumber: session.staffNumber, name: session.displayName } : undefined,
        payments: parsed.map((p) => ({ method: p.row.method, tenderedMinor: p.minor as bigint, reference: p.row.reference }))
      });
      router.replace(`/receipt?seq=${entry.sequence}`);
    } catch (e) {
      setError(e instanceof NotEnrolledError || e instanceof SaleRefusedError || e instanceof Error ? e.message : "The sale could not be recorded.");
      setBusy(false);
    }
  }

  return (
    <div className="max-w-2xl space-y-3">
      <PageHeader title="Take payment" sub={`${tab.label || "Unnamed tab"}: ${tab.lines.length} line${tab.lines.length === 1 ? "" : "s"}`} />
      <Card className="flex items-baseline justify-between">
        <span className="text-xs text-muted">Total due (net {fmt(totals.net.minor)} + tax {fmt(totals.tax.minor)})</span>
        <span className="text-2xl font-semibold tabular-nums">{fmt(totals.total.minor)}</span>
      </Card>

      <Notice tone="info" title="M-Pesa is simulated in this build">
        The prompt below never reaches Safaricom and no money moves. The receipt it returns is prefixed MOCK and is recorded in the signed
        journal as such. A phone ending in 00 plays a customer who declines.
      </Notice>

      {s.lease ? null : (
        <Notice tone="warn" title="This sale will be FISCAL_PENDING">
          No fiscal lease is held and the service that issues them is not built, so this sale is recorded and signed but carries
          no fiscal invoice number. The receipt will say so.
        </Notice>
      )}

      <Card className="space-y-2">
        <SectionTitle>Split the bill</SectionTitle>
        <div className="flex items-end gap-2">
          <div className="w-24">
            <Field label="Shares">
              <Input value={people} inputMode="numeric" onChange={(e) => setPeople(e.target.value)} />
            </Field>
          </div>
          <Button onClick={split}>Split evenly</Button>
        </div>
        <p className="text-xs text-muted">Any odd minor unit goes to the first shares, so the shares always add up to the total exactly.</p>
      </Card>

      <Card className="space-y-2">
        <SectionTitle>Payments</SectionTitle>
        {rows.map((r, i) => (
          <div key={r.key} className="space-y-1">
          <div className="grid grid-cols-[110px_1fr_1fr_auto] items-end gap-2">
            <Field label={i === 0 ? "Method" : "Method "}>
              <Select value={r.method} onChange={(e) => update(r.key, { method: e.target.value as PayMethod })}>
                <option value="CASH">Cash</option>
                <option value="MOBILE_MONEY">Mobile money</option>
              </Select>
            </Field>
            <Field label={`Amount handed over, ${cur}`}>
              <Input value={r.amount} inputMode="decimal" onChange={(e) => update(r.key, { amount: e.target.value })} autoComplete="off" />
            </Field>
            <Field label="Reference" hint={r.method === "MOBILE_MONEY" ? "Typed by the cashier, or filled by the simulated prompt; not verified." : undefined}>
              <Input value={r.reference} maxLength={40} onChange={(e) => update(r.key, { reference: e.target.value })} autoComplete="off" />
            </Field>
            <Button aria-label="Remove payment" disabled={rows.length === 1} onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}>
              Remove
            </Button>
          </div>
          {r.method === "MOBILE_MONEY" ? (
            <div className="grid grid-cols-[110px_1fr_auto] items-end gap-2">
              <span className="pb-2 text-2xs text-muted">M-Pesa (simulated)</span>
              <Field label="Customer phone">
                <Input value={phones[r.key] ?? ""} inputMode="tel" placeholder="0712 345 678" onChange={(e) => setPhones((p) => ({ ...p, [r.key]: e.target.value }))} autoComplete="off" />
              </Field>
              <Button disabled={prompting !== null} onClick={() => requestMobile(r.key, parsed.find((p) => p.row.key === r.key)?.minor ?? null)}>
                {prompting === r.key ? "Waiting..." : "Send prompt"}
              </Button>
              {promptNote[r.key] ? <p className="col-span-3 text-2xs text-muted">{promptNote[r.key]}</p> : null}
            </div>
          ) : null}
          </div>
        ))}
        <div className="flex gap-2">
          <Button onClick={() => setRows((rs) => [...rs, { key: rowKey++, method: "CASH", amount: outstanding > 0n ? format(money(outstanding, cur)).slice(cur.length + 1) : "", reference: "" }])}>
            Add payment
          </Button>
        </div>
        <div className="border-t border-line pt-2 text-xs">
          <div className="flex justify-between"><span className="text-muted">Handed over</span><span className="tabular-nums">{fmt(tendered)}</span></div>
          {outstanding > 0n ? (
            <div className="flex justify-between text-warn"><span>Still to pay</span><span className="tabular-nums">{fmt(outstanding)}</span></div>
          ) : (
            <div className="flex justify-between"><span className="text-muted">Change to give</span><span className="tabular-nums">{fmt(-outstanding)}</span></div>
          )}
        </div>
        {result && !result.ok ? <p role="alert" className="text-xs text-danger">{result.error}</p> : null}
      </Card>

      {error ? <Notice tone="danger" title="Nothing was recorded">{error}</Notice> : null}
      <div className="flex gap-2">
        <Button variant="primary" disabled={!result?.ok || busy} onClick={settle}>
          {busy ? "Signing..." : "Complete sale"}
        </Button>
        <LinkButton href={`/sale/tab?id=${encodeURIComponent(tab.id)}`}>Back to tab</LinkButton>
      </div>
    </div>
  );
}

export default function TenderPage() {
  return (
    <Suspense fallback={<Loading />}>
      <RequireStaff><Tender /></RequireStaff>
    </Suspense>
  );
}

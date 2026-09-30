import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { Badge, Card, EmptyState, Notice, PageHeader, Stat, Table, buttonClass, rowClass } from "@/components/ui";

interface WastageRow { id: string; product: string; supplier: string; quantity: number; reason: string; valueCents: number; recordedAt: string }

export default async function WastagePage() {
  let records: WastageRow[] = [];
  let total = 0;
  let error: string | null = null;
  try {
    records = await api.get<WastageRow[]>("/v1/wastage/records?limit=100");
    total = (await api.get<{ totalValueCents: number }>("/v1/wastage")).totalValueCents;
  } catch (caught) {
    error = describeError(caught);
  }
  if (error) return (<><PageHeader title="Wastage" /><Notice tone="danger">{error}</Notice></>);

  const byReason = records.reduce<Map<string, number>>((m, r) => m.set(r.reason, (m.get(r.reason) ?? 0) + r.valueCents), new Map());
  return (
    <>
      <PageHeader title="Wastage" subtitle="Stock that spoiled or broke. Each record writes the quantity off the supplier's offer at its cost price."
        actions={<Link href="/wastage/new" className={buttonClass}>Record wastage</Link>} />
      <div className="grid gap-3 sm:grid-cols-4">
        <Stat label="Written off" value={ksh(total)} hint="at supplier cost" tone="danger" />
        <Stat label="Records" value={String(records.length)} hint="most recent 100" />
        {[...byReason.entries()].slice(0, 2).map(([reason, value]) => (
          <Stat key={reason} label={reason.toLowerCase()} value={ksh(value)} hint="by reason" tone="warn" />
        ))}
      </div>
      <div className="mt-6">
        <Card>
          {records.length === 0 ? (
            <EmptyState message="No wastage recorded" detail="When stock spoils, record it here so the margin figures stay honest." />
          ) : (
            <Table head={["Recorded", "Product", "Supplier", "Qty", "Reason", "Value"]}>
              {records.map((r) => (
                <tr key={r.id} className={rowClass}>
                  <td className="px-3.5 py-2.5 text-[var(--color-muted)]">{new Date(r.recordedAt).toLocaleDateString("en-KE", { day: "numeric", month: "short" })}</td>
                  <td className="px-3.5 py-2.5 font-medium">{r.product}</td>
                  <td className="px-3.5 py-2.5 text-[var(--color-muted)]">{r.supplier}</td>
                  <td className="px-3.5 py-2.5 tabular-nums">{r.quantity}</td>
                  <td className="px-3.5 py-2.5"><Badge value={r.reason} /></td>
                  <td className="px-3.5 py-2.5 tabular-nums text-[var(--color-danger)]">{ksh(r.valueCents)}</td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      </div>
    </>
  );
}

import { api, describeError, ksh } from "@/lib/api";
import { Badge, Notice, PageHeader, Stat } from "@/components/ui";
import { FulfilmentRow } from "@/components/fulfilment-row";

interface Fulfilment {
  lineId: string;
  product: string;
  reference: string;
  customer: string;
  county: string;
  quantity: number;
  unitPayoutCents: number;
  status: string;
  placedAt: string;
  trackingNote: string | null;
}

export default async function SupplierPage() {
  let rows: Fulfilment[] = [];
  let error: string | null = null;

  try {
    rows = await api.get<Fulfilment[]>("/v1/supplier/fulfilments?limit=100");
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="Orders to fill" /><Notice tone="danger">{error}</Notice></>);
  }

  const waiting = rows.filter((r) => r.status === "ROUTED");
  const moving = rows.filter((r) => r.status === "DISPATCHED");
  const done = rows.filter((r) => r.status === "DELIVERED");
  const owed = rows
    .filter((r) => r.status !== "DELIVERED")
    .reduce((total, r) => total + r.unitPayoutCents * r.quantity, 0);

  return (
    <>
      <PageHeader
        title="Orders to fill"
        subtitle="Lines routed to you. Mark each one dispatched when it leaves, then delivered on arrival."
      />

      <div className="grid gap-3 sm:grid-cols-4">
        <Stat label="Waiting on you" value={String(waiting.length)} tone={waiting.length ? "warn" : undefined} />
        <Stat label="On the road" value={String(moving.length)} />
        <Stat label="Delivered" value={String(done.length)} />
        <Stat label="Owed to you" value={ksh(owed)} hint="not yet delivered" />
      </div>

      <div className="mt-6 space-y-3">
        {rows.length === 0 ? (
          <p className="text-[0.8125rem] text-[var(--color-muted)]">
            Nothing has been routed to you yet.
          </p>
        ) : (
          rows.map((row) => <FulfilmentRow key={row.lineId} row={row} />)
        )}
      </div>
    </>
  );
}

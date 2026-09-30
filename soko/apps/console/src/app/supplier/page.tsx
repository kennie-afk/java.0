import { api, describeError, ksh } from "@/lib/api";
import { Notice, PageHeader, Stat } from "@/components/ui";
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

const TABS = [
  { status: "ROUTED", label: "Waiting on you" },
  { status: "DISPATCHED", label: "On the road" },
  { status: "DELIVERED", label: "Delivered" }
];

interface Summary { waiting: number; onTheRoad: number; delivered: number; owedCents: number }

export default async function SupplierPage({ searchParams }: { searchParams: Promise<{ status?: string }> }) {
  const { status: requested } = await searchParams;
  const status = TABS.some((t) => t.status === requested) ? requested! : "ROUTED";
  let summary: Summary | null = null;
  let rows: Fulfilment[] = [];
  let error: string | null = null;

  try {
    summary = await api.get<Summary>("/v1/supplier/fulfilments/summary");
    rows = await api.get<Fulfilment[]>(`/v1/supplier/fulfilments?status=${status}&limit=50`);
  } catch (caught) {
    error = describeError(caught);
  }

  if (error || !summary) {
    return (<><PageHeader title="Orders to fill" /><Notice tone="danger">{error ?? "Could not load."}</Notice></>);
  }

  return (
    <>
      <PageHeader
        title="Orders to fill"
        subtitle="Lines routed to you. Mark each one dispatched when it leaves, then delivered on arrival."
      />

      <div className="grid gap-3 sm:grid-cols-4">
        <Stat label="Waiting on you" value={String(summary.waiting)} tone={summary.waiting ? "warn" : undefined} />
        <Stat label="On the road" value={String(summary.onTheRoad)} />
        <Stat label="Delivered" value={String(summary.delivered)} />
        <Stat label="Owed to you" value={ksh(summary.owedCents)} hint="not yet delivered" />
      </div>

      <nav className="mt-6 flex flex-wrap gap-1" aria-label="Filter by status">
        {TABS.map((tab) => (
          <a key={tab.status} href={`/supplier?status=${tab.status}`} aria-current={tab.status === status ? "page" : undefined}
            className={`rounded-md px-3 py-1.5 text-[0.958rem] font-medium transition-colors ${
              tab.status === status ? "bg-[var(--color-ink)] text-white" : "text-[var(--color-muted)] hover:bg-[var(--color-raised)]"}`}>
            {tab.label}
          </a>
        ))}
      </nav>

      <div className="mt-4 space-y-3">
        {rows.length === 0 ? (
          <p className="text-[0.958rem] text-[var(--color-muted)]">Nothing in this list.</p>
        ) : (
          rows.map((row) => <FulfilmentRow key={row.lineId} row={row} />)
        )}
        {rows.length === 50 ? (
          <p className="text-[0.833rem] text-[var(--color-faint)]">Showing the 50 most recent.</p>
        ) : null}
      </div>
    </>
  );
}

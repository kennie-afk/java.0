"use client";

import { useActionState } from "react";
import { deliverLine, dispatchLine, type PortalState } from "@/app/supplier/actions";
import { Badge, buttonClass, inputClass, secondaryButtonClass } from "@/components/ui";
import { ksh } from "@/lib/money";

const INITIAL: PortalState = { error: null, message: null };

interface Row {
  lineId: string;
  product: string;
  reference: string;
  customer: string;
  county: string;
  quantity: number;
  unitPayoutCents: number;
  status: string;
  trackingNote: string | null;
}

export function FulfilmentRow({ row }: { row: Row }) {
  const [dispatchState, dispatchAction, dispatching] = useActionState(dispatchLine, INITIAL);
  const [deliverState, deliverAction, delivering] = useActionState(deliverLine, INITIAL);
  const error = dispatchState.error ?? deliverState.error;

  return (
    <div className="rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <p className="text-[0.9375rem] font-medium">
            {row.product} <span className="text-[var(--color-muted)]">× {row.quantity}</span>
          </p>
          <p className="mt-0.5 text-[0.75rem] text-[var(--color-muted)]">
            {row.reference} · {row.customer} · {row.county}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-[0.875rem] font-medium tabular-nums">
            {ksh(row.unitPayoutCents * row.quantity)}
          </span>
          <Badge value={row.status} />
        </div>
      </div>

      {row.trackingNote ? (
        <p className="mt-2 text-[0.75rem] text-[var(--color-faint)]">{row.trackingNote}</p>
      ) : null}

      {error ? <p className="mt-2 text-[0.75rem] text-[var(--color-danger)]">{error}</p> : null}

      {row.status === "ROUTED" ? (
        <form action={dispatchAction} className="mt-3 flex flex-wrap items-center gap-2">
          <input type="hidden" name="lineId" value={row.lineId} />
          <input name="trackingNote" placeholder="How it is travelling, optional"
            className={`${inputClass} mt-0 max-w-xs`} />
          <button type="submit" className={buttonClass} disabled={dispatching}>
            {dispatching ? "Saving…" : "Mark dispatched"}
          </button>
        </form>
      ) : row.status === "DISPATCHED" ? (
        <form action={deliverAction} className="mt-3">
          <input type="hidden" name="lineId" value={row.lineId} />
          <button type="submit" className={secondaryButtonClass} disabled={delivering}>
            {delivering ? "Saving…" : "Mark delivered"}
          </button>
        </form>
      ) : null}
    </div>
  );
}

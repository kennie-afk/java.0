"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { checkPayment, startPayment, type PayState } from "@/app/shop/pay-actions";
import { buttonClass, inputClass, secondaryButtonClass } from "@/components/ui";
import { ksh } from "@/lib/money";

const POLL_MS = 3000;
// An M-Pesa prompt lapses on the phone after about a minute; two minutes covers a slow answer.
const GIVE_UP_AFTER_POLLS = 40;

/**
 * Pay for an order with M-Pesa: ask for the number, send the prompt, then watch the order until
 * the phone is answered. A refused or cancelled prompt shows why and offers another try; a prompt
 * nobody answers stops polling and says so instead of spinning for ever.
 */
export function PayPanel({
  orderId,
  totalCents,
  defaultPhone = "",
  onPaid
}: {
  orderId: string;
  totalCents: number;
  defaultPhone?: string;
  onPaid?: () => void;
}) {
  const [phone, setPhone] = useState(defaultPhone);
  const [state, setState] = useState<PayState>({ phase: "idle", detail: null, receipt: null });
  const [sending, setSending] = useState(false);
  const [gaveUp, setGaveUp] = useState(false);
  const polls = useRef(0);
  const paidReported = useRef(false);

  const refresh = useCallback(async () => {
    const next = await checkPayment(orderId);
    setState(next);
    if (next.phase === "paid" && !paidReported.current) {
      paidReported.current = true;
      onPaid?.();
    }
    return next;
  }, [orderId, onPaid]);

  // Pick up where an earlier visit left off: a reload mid-prompt resumes watching, a paid order shows as paid.
  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (state.phase !== "waiting" || gaveUp) return;
    const timer = window.setInterval(async () => {
      polls.current += 1;
      const next = await refresh();
      if (next.phase === "waiting" && polls.current >= GIVE_UP_AFTER_POLLS) {
        setGaveUp(true);
      }
    }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [state.phase, gaveUp, refresh]);

  const send = async () => {
    setSending(true);
    setGaveUp(false);
    polls.current = 0;
    setState(await startPayment(orderId, phone));
    setSending(false);
  };

  if (state.phase === "paid") {
    return (
      <div className="rounded-xl border border-[#c8e9db] bg-[var(--color-good-soft)] p-4 text-[0.958rem] text-[var(--color-good)]" role="status">
        <p className="font-medium">Paid {ksh(totalCents)} with M-Pesa. Thank you.</p>
        {state.receipt ? <p className="mt-1">Receipt {state.receipt}</p> : null}
      </div>
    );
  }

  const waiting = state.phase === "waiting" && !gaveUp;

  return (
    <div className="rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] p-4">
      <p className="text-[1.083rem] font-semibold">Pay {ksh(totalCents)} with M-Pesa</p>
      <p className="mt-1 text-[0.958rem] text-[var(--color-muted)]">
        We send a prompt to your phone; enter your M-Pesa PIN to approve it.
      </p>

      {waiting ? (
        <p className="mt-3 text-[0.958rem]" role="status">
          Prompt sent. Check your phone and enter your PIN. This page updates when M-Pesa confirms.
        </p>
      ) : (
        <div className="mt-3 flex flex-wrap items-end gap-2">
          <label className="block">
            <span className="block text-[0.875rem] font-medium">M-Pesa number</span>
            <input value={phone} onChange={(event) => setPhone(event.target.value)}
              inputMode="tel" autoComplete="tel" placeholder="0712 345 678"
              className={`${inputClass} !mt-1 w-48`} />
          </label>
          <button type="button" onClick={send} disabled={sending} className={buttonClass}>
            {sending ? "Sending…" : state.phase === "failed" || gaveUp ? "Try again" : "Pay now"}
          </button>
        </div>
      )}

      {state.phase === "failed" && state.detail ? (
        <p className="mt-3 text-[0.958rem] text-[var(--color-danger)]" role="alert">{state.detail}</p>
      ) : null}
      {gaveUp ? (
        <div className="mt-3 text-[0.958rem]" role="status">
          <p className="text-[var(--color-muted)]">
            Nothing has come back from M-Pesa yet. If you already approved it, check again in a moment;
            otherwise send a new prompt.
          </p>
          <button type="button" className={`${secondaryButtonClass} mt-2`}
            onClick={() => { setGaveUp(false); polls.current = 0; void refresh(); }}>
            Check again
          </button>
        </div>
      ) : null}
    </div>
  );
}

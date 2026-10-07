"use server";

import { api, describeError } from "@/lib/api";

export type PayPhase = "idle" | "waiting" | "paid" | "failed";

export interface PayState {
  phase: PayPhase;
  detail: string | null;
  receipt: string | null;
}

/** 0712 345 678, +254712345678 and 254712345678 all become 254712345678; null when it is not one. */
function normaliseMsisdn(raw: string): string | null {
  let digits = raw.replace(/[\s\-()]/g, "");
  if (digits.startsWith("+")) digits = digits.slice(1);
  if (digits.startsWith("0")) digits = `254${digits.slice(1)}`;
  return /^254[17]\d{8}$/.test(digits) ? digits : null;
}

interface PaymentView {
  paid: boolean;
  orderStatus: string;
  status?: "PENDING" | "SUCCESS" | "FAILED" | "ORPHANED";
  detail?: string | null;
  receipt?: string | null;
}

function toState(view: PaymentView): PayState {
  if (view.paid || view.status === "SUCCESS") {
    return { phase: "paid", detail: null, receipt: view.receipt ?? null };
  }
  if (view.status === "PENDING") {
    return { phase: "waiting", detail: view.detail ?? null, receipt: null };
  }
  if (view.status === "FAILED") {
    return { phase: "failed", detail: view.detail ?? "The payment did not go through.", receipt: null };
  }
  if (view.status === "ORPHANED") {
    // Money left the phone but could not be applied: tell them truthfully, not "failed, try again".
    return {
      phase: "failed",
      detail: "Your payment was received but could not be matched to this order. Contact the shop with your M-Pesa receipt; do not pay again.",
      receipt: view.receipt ?? null
    };
  }
  return { phase: "idle", detail: null, receipt: null };
}

/** Sends the M-Pesa prompt to the buyer's phone for this order. */
export async function startPayment(orderId: string, phone: string): Promise<PayState> {
  const msisdn = normaliseMsisdn(phone);
  if (!msisdn) {
    return { phase: "failed", detail: "Enter a Safaricom number such as 0712 345 678.", receipt: null };
  }
  try {
    const started = await api.post<{ status: string; detail: string | null }>(
      `/v1/shop/orders/${orderId}/pay`,
      { msisdn }
    );
    if (started.status === "FAILED") {
      return { phase: "failed", detail: started.detail ?? "M-Pesa did not accept the request.", receipt: null };
    }
    return { phase: "waiting", detail: started.detail, receipt: null };
  } catch (caught) {
    return { phase: "failed", detail: describeError(caught), receipt: null };
  }
}

/** Where this order's payment stands; polled while the buyer answers the prompt. */
export async function checkPayment(orderId: string): Promise<PayState> {
  try {
    return toState(await api.get<PaymentView>(`/v1/shop/orders/${orderId}/payment`));
  } catch (caught) {
    // A failed poll is not a failed payment: keep waiting and say what went wrong.
    return { phase: "waiting", detail: describeError(caught), receipt: null };
  }
}

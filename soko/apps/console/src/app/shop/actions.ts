"use server";

import { revalidatePath } from "next/cache";
import { api, describeError } from "@/lib/api";

export interface CheckoutState {
  error: string | null;
  reference: string | null;
  orderId: string | null;
}

export async function checkout(_previous: CheckoutState, form: FormData): Promise<CheckoutState> {
  const raw = String(form.get("basket") ?? "[]");

  let lines: { productId: string; quantity: number }[];
  try {
    lines = JSON.parse(raw);
  } catch {
    return { error: "That basket could not be read.", reference: null, orderId: null };
  }

  if (!Array.isArray(lines) || lines.length === 0) {
    return { error: "Your basket is empty.", reference: null, orderId: null };
  }

  try {
    // The key travels with the form, so a double submit or a resend returns the first order.
    const key = String(form.get("idempotencyKey") ?? "");
    const placed = key
      ? await api.postOnce<{ reference: string; orderId: string }>("/v1/shop/orders", { lines }, key)
      : await api.post<{ reference: string; orderId: string }>("/v1/shop/orders", { lines });
    revalidatePath("/shop/orders");
    return { error: null, reference: placed.reference, orderId: placed.orderId };
  } catch (caught) {
    return { error: describeError(caught), reference: null, orderId: null };
  }
}

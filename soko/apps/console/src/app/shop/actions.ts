"use server";

import { revalidatePath } from "next/cache";
import { api, describeError } from "@/lib/api";

export interface CheckoutState {
  error: string | null;
  reference: string | null;
}

export async function checkout(_previous: CheckoutState, form: FormData): Promise<CheckoutState> {
  const raw = String(form.get("basket") ?? "[]");

  let lines: { productId: string; quantity: number }[];
  try {
    lines = JSON.parse(raw);
  } catch {
    return { error: "That basket could not be read.", reference: null };
  }

  if (!Array.isArray(lines) || lines.length === 0) {
    return { error: "Your basket is empty.", reference: null };
  }

  try {
    const placed = await api.post<{ reference: string }>("/v1/shop/orders", { lines });
    revalidatePath("/shop/orders");
    return { error: null, reference: placed.reference };
  } catch (caught) {
    return { error: describeError(caught), reference: null };
  }
}

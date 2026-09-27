"use server";

import { api, describeError } from "@/lib/api";

export interface GuestCheckoutLine {
  productId: string;
  quantity: number;
}

export interface GuestCheckoutInput {
  slug: string;
  customerName: string;
  phone: string;
  county: string;
  lines: GuestCheckoutLine[];
}

export interface GuestOrderLine {
  product: string;
  quantity: number;
  unitPriceCents: number;
  lineTotalCents: number;
}

export interface GuestOrder {
  orderId: string;
  reference: string;
  totalCents: number;
  lines: GuestOrderLine[];
}

export interface GuestCheckoutResult {
  order: GuestOrder | null;
  error: string | null;
}

// A plain server action, not a route the browser calls directly: the
// unauthenticated visitor's browser only ever talks to this Next.js server,
// which then makes the actual call to the API server-side (the same pattern
// every other action in this app already uses) - so nothing about the API's
// internal address needs to be exposed to the client at all.
export async function placeGuestOrder(input: GuestCheckoutInput): Promise<GuestCheckoutResult> {
  if (input.lines.length === 0) {
    return { order: null, error: "Your cart is empty." };
  }
  if (!input.customerName.trim() || !input.phone.trim() || !input.county.trim()) {
    return { order: null, error: "Name, phone and county are all required." };
  }

  try {
    const order = await api.post<GuestOrder>(`/v1/public/${input.slug}/orders`, {
      customerName: input.customerName.trim(),
      phone: input.phone.trim(),
      county: input.county.trim(),
      lines: input.lines.map((line) => ({ productId: line.productId, quantity: line.quantity })),
    });
    return { order, error: null };
  } catch (caught) {
    return { order: null, error: describeError(caught) };
  }
}

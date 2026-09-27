"use server";

import { api, describeError } from "@/lib/api";
import { writeSession } from "@/lib/session";

export interface CartLineInput {
  productId: string;
  quantity: number;
}

export interface OrderLineResult {
  product: string;
  quantity: number;
  unitPriceCents: number;
  lineTotalCents: number;
}

export interface OrderResult {
  orderId: string;
  reference: string;
  totalCents: number;
  lines: OrderLineResult[];
}

export interface OrderOutcome {
  order: OrderResult | null;
  error: string | null;
}

interface SessionResult {
  accessToken: string;
  fullName: string;
  role: string;
  organisation: string;
  expiresInSeconds: number;
}

async function placeOrder(lines: CartLineInput[]): Promise<OrderOutcome> {
  try {
    const order = await api.post<OrderResult>("/v1/shop/orders", { lines });
    return { order, error: null };
  } catch (caught) {
    return { order: null, error: describeError(caught) };
  }
}

// Ordering requires an account: a first-time visitor registers, an existing
// buyer signs in - either way we get a real session and then place the order
// through the same authenticated /v1/shop/orders every other customer uses,
// not a separate unauthenticated path.

export async function signInAndOrder(input: {
  email: string;
  password: string;
  lines: CartLineInput[];
}): Promise<OrderOutcome> {
  if (!input.email.trim() || !input.password) {
    return { order: null, error: "Enter both an email address and a password." };
  }

  try {
    const result = await api.login(input.email.trim(), input.password);
    if (result.role !== "CUSTOMER") {
      return { order: null, error: "That account isn't a customer account for this storefront." };
    }
    await writeSession(
      {
        token: result.accessToken,
        fullName: result.fullName,
        role: result.role,
        organisation: result.organisation,
      },
      result.expiresInSeconds
    );
  } catch (caught) {
    return { order: null, error: describeError(caught) };
  }

  return placeOrder(input.lines);
}

export async function registerAndOrder(input: {
  slug: string;
  fullName: string;
  email: string;
  phone: string;
  county: string;
  password: string;
  lines: CartLineInput[];
}): Promise<OrderOutcome> {
  if (
    !input.fullName.trim() ||
    !input.email.trim() ||
    !input.phone.trim() ||
    !input.county.trim()
  ) {
    return { order: null, error: "Every field is needed to open an account." };
  }
  if (input.password.length < 10) {
    return { order: null, error: "Use a password of at least 10 characters." };
  }

  try {
    const result = await api.post<SessionResult>(`/v1/public/${input.slug}/register`, {
      fullName: input.fullName.trim(),
      email: input.email.trim(),
      phone: input.phone.trim(),
      county: input.county.trim(),
      password: input.password,
    });
    await writeSession(
      {
        token: result.accessToken,
        fullName: result.fullName,
        role: result.role,
        organisation: result.organisation,
      },
      result.expiresInSeconds
    );
  } catch (caught) {
    return { order: null, error: describeError(caught) };
  }

  return placeOrder(input.lines);
}

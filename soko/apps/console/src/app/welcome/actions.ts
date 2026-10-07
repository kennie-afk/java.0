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

export interface SimpleOutcome {
  error: string | null;
}

interface SessionResult {
  accessToken: string;
  fullName: string;
  role: string;
  organisation: string;
  expiresInSeconds: number;
}

// Ordering requires an account, and the account is phone + a one-time code,
// not email + password - almost nobody in this market authenticates with a
// password day to day, but everyone already trusts a code sent to their
// phone. Requesting the code and verifying it are two separate actions so
// the drawer can show a "check your phone" step in between; verifying signs
// the buyer in (opening an account on the spot if this number is new) and
// places the order in the same action, through the same authenticated
// /v1/shop/orders every signed-in customer uses.

export async function requestOtp(slug: string, phone: string): Promise<SimpleOutcome> {
  if (!phone.trim()) {
    return { error: "Enter a phone number." };
  }
  try {
    await api.post(`/v1/public/${slug}/otp/request`, { phone: phone.trim() });
    return { error: null };
  } catch (caught) {
    return { error: describeError(caught) };
  }
}

export async function verifyOtpAndOrder(input: {
  slug: string;
  phone: string;
  code: string;
  fullName: string;
  county: string;
  lines: CartLineInput[];
  /** Same key for a retry of the same basket, so one tap-happy buyer cannot place two orders. */
  idempotencyKey: string;
}): Promise<OrderOutcome> {
  if (!input.code.trim()) {
    return { order: null, error: "Enter the code we sent you." };
  }

  try {
    const result = await api.post<SessionResult>(`/v1/public/${input.slug}/otp/verify`, {
      phone: input.phone.trim(),
      code: input.code.trim(),
      fullName: input.fullName.trim(),
      county: input.county.trim(),
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

  try {
    const order = await api.postOnce<OrderResult>(
      "/v1/shop/orders",
      { lines: input.lines },
      input.idempotencyKey
    );
    return { order, error: null };
  } catch (caught) {
    return { order: null, error: describeError(caught) };
  }
}

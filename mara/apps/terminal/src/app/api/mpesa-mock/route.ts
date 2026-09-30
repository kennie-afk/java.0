import { randomBytes } from "node:crypto";
import { NextResponse } from "next/server";

/**
 * A SIMULATED M-Pesa prompt. No request is ever made to Safaricom and no money moves: this
 * exists so a demo can show the mobile-money tender flow end to end. Every receipt it issues
 * is prefixed MOCK and the response says `mock: true`; the till stores that prefix in the
 * signed journal, so a simulated payment can never be mistaken for a real one afterwards.
 *
 * A real Daraja integration belongs on the server that holds the credentials, never in the
 * browser-facing till. MPESA_MODE=daraja is therefore refused here rather than half-done.
 *
 * Test hook for the demo: a phone number ending in 00 is treated as the customer declining
 * the prompt.
 */
export const dynamic = "force-dynamic";

const PHONE = /^(?:\+?254|0)[17]\d{8}$/;

export async function POST(request: Request) {
  if ((process.env.MPESA_MODE ?? "mock") !== "mock") {
    return NextResponse.json(
      { error: "not_available", message: "Only the simulated M-Pesa flow exists in this build." },
      { status: 501 }
    );
  }
  let input: unknown;
  try {
    input = await request.json();
  } catch {
    return NextResponse.json({ error: "bad_request", message: "Body must be JSON." }, { status: 400 });
  }
  const { phone, amountMinor } = (input ?? {}) as Record<string, unknown>;
  if (typeof phone !== "string" || !PHONE.test(phone.replace(/\s/g, ""))) {
    return NextResponse.json({ error: "bad_phone", message: "Enter a Safaricom number such as 0712 345 678." }, { status: 400 });
  }
  if (typeof amountMinor !== "string" || !/^\d{1,12}$/.test(amountMinor) || BigInt(amountMinor) <= 0n) {
    return NextResponse.json({ error: "bad_amount", message: "Amount must be a positive number of minor units." }, { status: 400 });
  }
  await new Promise((r) => setTimeout(r, 1200)); // the customer is looking at their phone
  if (phone.replace(/\s/g, "").endsWith("00")) {
    return NextResponse.json({ mock: true, status: "CANCELLED", message: "The customer declined the prompt." });
  }
  const receipt = "MOCK" + randomBytes(5).toString("hex").toUpperCase().slice(0, 8);
  return NextResponse.json({ mock: true, status: "SUCCESS", receipt, amountMinor });
}

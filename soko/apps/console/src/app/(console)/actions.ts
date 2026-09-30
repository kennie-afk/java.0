"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { api, describeError } from "@/lib/api";
import type { FormState } from "@/components/action-form";

const fail = (caught: unknown): FormState => ({ error: describeError(caught), message: null });
const ok = (message: string): FormState => ({ error: null, message });

export async function changePlan(_p: FormState, form: FormData): Promise<FormState> {
  const plan = String(form.get("plan") ?? "");
  try {
    await api.request("PUT", "/v1/billing/subscription/plan", { plan });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/billing");
  redirect(`/billing?notice=plan-${encodeURIComponent(plan)}`);
}

export async function generateInvoice(_p: FormState, form: FormData): Promise<FormState> {
  const from = String(form.get("from") ?? "");
  const to = String(form.get("to") ?? "");
  if (!from || !to) return { error: "Choose both dates.", message: null };
  if (from > to) return { error: "The period must end after it starts.", message: null };
  try {
    const invoice = await api.post<{ reference: string; totalCents: number }>("/v1/billing/invoices/generate", {
      periodStart: `${from}T00:00:00Z`,
      periodEnd: `${to}T23:59:59Z`
    });
    revalidatePath("/billing");
    return ok(`Issued ${invoice.reference}.`);
  } catch (caught) {
    return fail(caught);
  }
}

export async function payInvoice(_p: FormState, form: FormData): Promise<FormState> {
  const id = String(form.get("invoiceId") ?? "");
  const msisdn = String(form.get("msisdn") ?? "").replace(/\D/g, "").replace(/^0/, "254");
  if (!/^254[17]\d{8}$/.test(msisdn)) {
    return { error: "Enter a Safaricom number such as 0712 345 678.", message: null };
  }
  try {
    await api.post(`/v1/billing/invoices/${id}/pay`, { msisdn });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/billing");
  return ok("Prompt sent. Approve it on the phone; the invoice flips to Paid when M-Pesa confirms.");
}

export async function recordWastage(_p: FormState, form: FormData): Promise<FormState> {
  const offerId = String(form.get("offerId") ?? "");
  const quantity = Number(form.get("quantity") ?? 0);
  const reason = String(form.get("reason") ?? "EXPIRED");
  if (!offerId) return { error: "Choose the offer the stock came from.", message: null };
  if (!Number.isInteger(quantity) || quantity < 1) return { error: "Enter a whole quantity of at least 1.", message: null };
  try {
    await api.post("/v1/wastage", { offerId, quantity, reason });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/wastage");
  redirect("/wastage");
}

export async function createCustomer(_p: FormState, form: FormData): Promise<FormState> {
  const name = String(form.get("name") ?? "").trim();
  const phone = String(form.get("phone") ?? "").trim();
  const county = String(form.get("county") ?? "").trim();
  if (!name || !phone || !county) return { error: "Name, phone and county are all needed.", message: null };
  try {
    await api.post("/v1/customers", { name, phone, county });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/customers");
  redirect("/customers");
}

export async function placeOrder(_p: FormState, form: FormData): Promise<FormState> {
  const customerId = String(form.get("customerId") ?? "");
  const lines: { productId: string; quantity: number }[] = [];
  for (let i = 0; i < 5; i += 1) {
    const productId = String(form.get(`product${i}`) ?? "");
    const quantity = Number(form.get(`quantity${i}`) ?? 0);
    if (productId && quantity > 0) lines.push({ productId, quantity });
  }
  if (!customerId) return { error: "Choose the customer.", message: null };
  if (lines.length === 0) return { error: "Add at least one product with a quantity.", message: null };
  let id: string;
  try {
    const order = await api.post<{ orderId: string }>("/v1/orders", { customerId, lines });
    id = order.orderId;
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/orders");
  redirect(`/orders/${id}`);
}

export async function cancelOrder(_p: FormState, form: FormData): Promise<FormState> {
  const id = String(form.get("orderId") ?? "");
  const reason = String(form.get("reason") ?? "").trim();
  try {
    await api.post(`/v1/orders/${id}/cancel`, { reason });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath(`/orders/${id}`);
  revalidatePath("/orders");
  redirect(`/orders/${id}?notice=cancelled`);
}

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
  const idempotencyKey = String(form.get("idempotencyKey") ?? "");
  const lines: { productId: string; quantity: number }[] = [];
  for (let i = 0; i < 5; i += 1) {
    const productId = String(form.get(`product${i}`) ?? "");
    const rawQuantity = String(form.get(`quantity${i}`) ?? "").trim();
    if (!productId && !rawQuantity) continue;
    const quantity = Number(rawQuantity);
    if (!productId) return { error: `Line ${i + 1} has a quantity but no product chosen from the list.`, message: null };
    if (!Number.isInteger(quantity) || quantity < 1) return { error: `Line ${i + 1} needs a whole quantity of at least 1.`, message: null };
    lines.push({ productId, quantity });
  }
  if (!customerId) return { error: "Choose the customer from the list.", message: null };
  if (lines.length === 0) return { error: "Add at least one product with a quantity.", message: null };
  let id: string;
  try {
    // The same key goes with every retry of this form, so a double click or a resend after a
    // timeout returns the first order instead of placing a second one.
    const order = idempotencyKey
      ? await api.postOnce<{ orderId: string }>("/v1/orders", { customerId, lines }, idempotencyKey)
      : await api.post<{ orderId: string }>("/v1/orders", { customerId, lines });
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

// ---- Catalogue, suppliers, offers and the team -------------------------------------------------

const bad = (error: string): FormState => ({ error, message: null });

/** "12.50" shillings to 1250 minor units, or null when it is not a positive money amount. */
function toCents(raw: string): number | null {
  const text = raw.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return null;
  const cents = Math.round(Number(text) * 100);
  return cents >= 1 ? cents : null;
}

function toWholeNumber(raw: string, min: number): number | null {
  const text = raw.trim();
  if (!/^\d+$/.test(text)) return null;
  const value = Number(text);
  return value >= min ? value : null;
}

/** "92" or "0.92" as typed by a person means 92 percent; the API takes 0..1. */
function toReliability(raw: string): number | null {
  const text = raw.trim();
  if (!text) return null;
  const value = Number(text);
  if (!Number.isFinite(value) || value < 0) return null;
  const fraction = value > 1 ? value / 100 : value;
  return fraction <= 1 ? Math.round(fraction * 1000) / 1000 : null;
}

export async function createSupplier(_p: FormState, form: FormData): Promise<FormState> {
  const name = String(form.get("name") ?? "").trim();
  const county = String(form.get("county") ?? "").trim();
  const leadTimeHours = toWholeNumber(String(form.get("leadTimeHours") ?? ""), 1);
  const reliabilityText = String(form.get("reliability") ?? "");
  const reliability = reliabilityText.trim() ? toReliability(reliabilityText) : undefined;
  if (!name) return bad("Give the supplier a name.");
  if (!county) return bad("Say which county the supplier ships from.");
  if (leadTimeHours === null) return bad("Lead time must be a whole number of hours, at least 1.");
  if (reliability === null) return bad("Reliability is a percentage from 0 to 100.");
  try {
    await api.post("/v1/suppliers", {
      name, county, leadTimeHours, coldChain: form.get("coldChain") === "on",
      ...(reliability === undefined ? {} : { reliability })
    });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/suppliers");
  redirect("/suppliers");
}

export async function updateSupplier(_p: FormState, form: FormData): Promise<FormState> {
  const id = String(form.get("id") ?? "");
  const name = String(form.get("name") ?? "").trim();
  const county = String(form.get("county") ?? "").trim();
  const leadTimeHours = toWholeNumber(String(form.get("leadTimeHours") ?? ""), 1);
  const reliability = toReliability(String(form.get("reliability") ?? ""));
  const status = String(form.get("status") ?? "");
  if (!id) return bad("That supplier could not be identified.");
  if (!name) return bad("Give the supplier a name.");
  if (!county) return bad("Say which county the supplier ships from.");
  if (leadTimeHours === null) return bad("Lead time must be a whole number of hours, at least 1.");
  if (reliability === null) return bad("Reliability is a percentage from 0 to 100.");
  if (status !== "ACTIVE" && status !== "INACTIVE") return bad("Choose Active or Inactive.");
  try {
    await api.request("PATCH", `/v1/suppliers/${id}`, {
      name, county, leadTimeHours, coldChain: form.get("coldChain") === "on", reliability, status
    });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/suppliers");
  redirect("/suppliers");
}

export async function createProduct(_p: FormState, form: FormData): Promise<FormState> {
  const sku = String(form.get("sku") ?? "").trim();
  const name = String(form.get("name") ?? "").trim();
  const category = String(form.get("category") ?? "").trim();
  const unit = String(form.get("unit") ?? "").trim();
  const shelfLifeHours = toWholeNumber(String(form.get("shelfLifeHours") ?? ""), 1);
  const listPriceCents = toCents(String(form.get("price") ?? ""));
  const photoUrl = String(form.get("photoUrl") ?? "").trim();
  if (!sku) return bad("Give the product a SKU, for example MLK-500.");
  if (!name) return bad("Give the product a name.");
  if (!category) return bad("Give the product a category.");
  if (!unit) return bad("Say how it is sold, for example 500 ml or kg.");
  if (shelfLifeHours === null) return bad("Shelf life must be a whole number of hours, at least 1.");
  if (listPriceCents === null) return bad("Enter the list price in shillings, for example 120 or 120.50.");
  try {
    await api.post("/v1/products", {
      sku, name, category, unit, shelfLifeHours, listPriceCents,
      perishable: form.get("perishable") === "on",
      requiresColdChain: form.get("requiresColdChain") === "on",
      photoUrl: photoUrl || null
    });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/catalogue");
  redirect("/catalogue");
}

export async function updateProduct(_p: FormState, form: FormData): Promise<FormState> {
  const id = String(form.get("id") ?? "");
  const name = String(form.get("name") ?? "").trim();
  const category = String(form.get("category") ?? "").trim();
  const shelfLifeHours = toWholeNumber(String(form.get("shelfLifeHours") ?? ""), 1);
  const listPriceCents = toCents(String(form.get("price") ?? ""));
  const photoUrl = String(form.get("photoUrl") ?? "").trim();
  if (!id) return bad("That product could not be identified.");
  if (!name) return bad("Give the product a name.");
  if (!category) return bad("Give the product a category.");
  if (shelfLifeHours === null) return bad("Shelf life must be a whole number of hours, at least 1.");
  if (listPriceCents === null) return bad("Enter the list price in shillings, for example 120 or 120.50.");
  try {
    await api.request("PATCH", `/v1/products/${id}`, { name, category, shelfLifeHours, listPriceCents, photoUrl });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/catalogue");
  redirect("/catalogue");
}

export async function createOffer(_p: FormState, form: FormData): Promise<FormState> {
  const supplierId = String(form.get("supplierId") ?? "");
  const productId = String(form.get("productId") ?? "");
  const costCents = toCents(String(form.get("cost") ?? ""));
  const availableQty = toWholeNumber(String(form.get("availableQty") ?? ""), 0);
  if (!supplierId) return bad("Choose the supplier from the list.");
  if (!productId) return bad("Choose the product from the list.");
  if (costCents === null) return bad("Enter what the supplier charges you, in shillings.");
  if (availableQty === null) return bad("Available quantity must be a whole number, 0 or more.");
  try {
    await api.post("/v1/offers", { supplierId, productId, costCents, availableQty });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/offers");
  redirect("/offers");
}

export async function inviteUser(_p: FormState, form: FormData): Promise<FormState> {
  const fullName = String(form.get("fullName") ?? "").trim();
  const email = String(form.get("email") ?? "").trim();
  const password = String(form.get("password") ?? "");
  const role = String(form.get("role") ?? "");
  const supplierId = String(form.get("supplierId") ?? "");
  const customerId = String(form.get("customerId") ?? "");
  if (!fullName) return bad("Give the person's name.");
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return bad("Enter a valid email address.");
  if (password.length < 10) return bad("Use a password of at least 10 characters.");
  if (!["OPERATOR", "SUPPLIER", "CUSTOMER"].includes(role)) return bad("Choose a role.");
  if (role === "SUPPLIER" && !supplierId) return bad("A supplier account needs a supplier chosen from the list.");
  if (role === "CUSTOMER" && !customerId) return bad("A customer account needs a customer chosen from the list.");
  try {
    await api.post("/v1/users", {
      fullName, email, password, role,
      supplierId: role === "SUPPLIER" ? supplierId : null,
      customerId: role === "CUSTOMER" ? customerId : null
    });
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/team");
  redirect("/team");
}

export async function setUserStatus(_p: FormState, form: FormData): Promise<FormState> {
  const id = String(form.get("userId") ?? "");
  const action = String(form.get("action") ?? "");
  if (!id || (action !== "suspend" && action !== "reactivate")) return bad("That change was not understood.");
  try {
    await api.post(`/v1/users/${id}/${action}`, {});
  } catch (caught) {
    return fail(caught);
  }
  revalidatePath("/team");
  return ok(action === "suspend" ? "Account suspended." : "Account reactivated.");
}

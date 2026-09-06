"use server";

import { revalidatePath } from "next/cache";
import { api, describeError } from "@/lib/api";

export interface PortalState {
  error: string | null;
  message: string | null;
}

export async function dispatchLine(_previous: PortalState, form: FormData): Promise<PortalState> {
  const lineId = String(form.get("lineId") ?? "");
  const trackingNote = String(form.get("trackingNote") ?? "").trim();

  try {
    await api.post(`/v1/supplier/fulfilments/${lineId}/dispatch`, { trackingNote });
  } catch (caught) {
    return { error: describeError(caught), message: null };
  }
  revalidatePath("/supplier");
  return { error: null, message: "Marked as dispatched." };
}

export async function deliverLine(_previous: PortalState, form: FormData): Promise<PortalState> {
  const lineId = String(form.get("lineId") ?? "");
  try {
    await api.post(`/v1/supplier/fulfilments/${lineId}/deliver`, {});
  } catch (caught) {
    return { error: describeError(caught), message: null };
  }
  revalidatePath("/supplier");
  return { error: null, message: "Marked as delivered." };
}

export async function updateOffer(_previous: PortalState, form: FormData): Promise<PortalState> {
  const offerId = String(form.get("offerId") ?? "");
  const costCents = Math.round(Number(form.get("cost") ?? 0) * 100);
  const availableQty = Number(form.get("availableQty") ?? 0);

  if (!Number.isFinite(costCents) || costCents < 1) {
    return { error: "Enter a price above zero.", message: null };
  }
  if (!Number.isFinite(availableQty) || availableQty < 0) {
    return { error: "Enter a quantity of zero or more.", message: null };
  }

  try {
    await api.put(`/v1/supplier/offers/${offerId}`, { costCents, availableQty });
  } catch (caught) {
    return { error: describeError(caught), message: null };
  }
  revalidatePath("/supplier/offers");
  return { error: null, message: "Offer updated." };
}

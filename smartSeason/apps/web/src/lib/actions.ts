"use server";

import { revalidatePath } from "next/cache";
import { api, ApiError } from "@/lib/api";
import { readToken } from "@/lib/session";
import type { FormState } from "@/lib/form-state";

function text(form: FormData, key: string): string | null {
  const value = form.get(key);
  if (typeof value !== "string" || value.trim() === "") {
    return null;
  }
  return value.trim();
}

function decimal(form: FormData, key: string): number | null {
  const value = text(form, key);
  if (value === null) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

async function submit(path: string, body: unknown, revalidate: string): Promise<FormState> {
  const token = await readToken();
  if (!token) {
    return { ok: false, message: "Your session has expired. Sign in again.", fieldErrors: {} };
  }

  try {
    await api.post(path, body, token);
    revalidatePath(revalidate);
    return { ok: true, message: null, fieldErrors: {} };
  } catch (error) {
    if (error instanceof ApiError) {
      return {
        ok: false,
        message: error.problem?.detail ?? "The request was rejected",
        fieldErrors: error.problem?.errors ?? {}
      };
    }
    return { ok: false, message: "The platform is not reachable right now", fieldErrors: {} };
  }
}

export async function createFarm(_prev: FormState, form: FormData): Promise<FormState> {
  return submit(
    "/api/farm/v1/farms",
    {
      name: text(form, "name"),
      county: text(form, "county"),
      subCounty: text(form, "subCounty"),
      totalAreaHa: decimal(form, "totalAreaHa"),
      latitude: decimal(form, "latitude"),
      longitude: decimal(form, "longitude"),
      status: "ACTIVE"
    },
    "/farms"
  );
}

export async function createWorker(_prev: FormState, form: FormData): Promise<FormState> {
  return submit(
    "/api/workforce/v1/workers",
    {
      fullName: text(form, "fullName"),
      phone: text(form, "phone"),
      nationalId: text(form, "nationalId"),
      farmId: text(form, "farmId"),
      status: "ACTIVE",
      riskScore: 0
    },
    "/workforce"
  );
}

export async function createListing(_prev: FormState, form: FormData): Promise<FormState> {
  return submit(
    "/api/marketplace/v1/supply-listings",
    {
      sellerOrgId: text(form, "sellerOrgId"),
      commodityCode: text(form, "commodityCode"),
      variety: text(form, "variety"),
      grade: text(form, "grade"),
      quantity: decimal(form, "quantity"),
      unit: text(form, "unit") ?? "kg",
      askPrice: decimal(form, "askPrice"),
      currency: "KES",
      county: text(form, "county"),
      status: "ACTIVE"
    },
    "/marketplace"
  );
}

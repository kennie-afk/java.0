"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { api, ApiError } from "@/lib/api";
import { CROPS } from "@/lib/crop-calendar";
import type { FormState } from "@/lib/form-state";
import { readToken } from "@/lib/session";

interface Plot {
  id: string;
  farmId: string;
  areaHa: number | null;
}

/**
 * Starts a season from four answers: which plot, what crop, when, and (optionally) what
 * yield is hoped for. The season service works out the status, harvest date and stage
 * calendar, so none of that is asked for here.
 */
export async function startSeason(_prev: FormState, form: FormData): Promise<FormState> {
  const token = await readToken();
  if (!token) redirect("/login");

  const values: Record<string, string> = {};
  for (const [key, value] of form.entries()) if (typeof value === "string") values[key] = value;
  const fail = (message: string, fieldErrors: Record<string, string> = {}): FormState =>
    ({ ok: false, message, fieldErrors, values });

  const plotId = values.plotId?.trim();
  const crop = CROPS.find((c) => c.code === values.cropCode);
  const startDate = values.startDate?.trim();
  if (!plotId) return fail("Choose a plot.", { plotId: "Required" });
  if (!crop) return fail("Choose a crop.", { cropCode: "Required" });
  if (!startDate) return fail("Choose the planting date.", { startDate: "Required" });

  try {
    const plot = await api.get<Plot>(`/api/farm/v1/plots/${plotId}`, token);
    let expected = values.expectedYieldKg?.trim() ? Number(values.expectedYieldKg) : null;
    if (expected !== null && !Number.isFinite(expected)) return fail("Expected yield must be a number.", { expectedYieldKg: "Not a number" });
    if (expected === null && plot.areaHa) expected = Math.round(plot.areaHa * crop.yieldKgPerHa);

    await api.post("/api/season/v1/seasons", {
      plotId,
      farmId: plot.farmId,
      cropCode: crop.code,
      variety: values.variety?.trim() || undefined,
      startDate,
      expectedYieldKg: expected ?? undefined
    }, token);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) redirect("/login");
      return fail(error.problem?.detail ?? "The request was rejected", error.problem?.errors ?? {});
    }
    return fail("The platform is not reachable right now");
  }
  revalidatePath("/season/seasons");
  revalidatePath("/");
  redirect("/season/seasons");
}

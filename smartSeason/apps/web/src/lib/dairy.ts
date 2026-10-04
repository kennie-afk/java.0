import { redirect } from "next/navigation";
import { api, ApiError, type PageResponse } from "@/lib/api";
import { readToken } from "@/lib/session";

export interface DairySummary {
  farmId: string;
  from: string;
  to: string;
  litresRecorded: number;
  litresDelivered: number;
  litresRejected: number;
  rejectionRatePct: number;
  weightedFatPct: number | null;
  deliveredValue: number;
  deliveriesWithoutPrice: number;
  milkingCows: number;
  days: { day: string; litres: number }[];
  cows: { cowId: string; tagNo: string; name: string | null; litres: number; daysRecorded: number; litresPerRecordedDay: number }[];
  underWithdrawal: { cowId: string; tagNo: string; medicine: string | null; endsOn: string }[];
  milkedDuringWithdrawal: {
    cowId: string; tagNo: string; day: string; litres: number; medicine: string | null; withdrawalEndsOn: string;
  }[];
  expectedCalvings: { cowId: string; tagNo: string; expectedOn: string }[];
}

export interface FarmOption {
  id: string;
  name: string;
}

async function token(): Promise<string> {
  const value = await readToken();
  if (!value) redirect("/login");
  return value;
}

export async function loadFarms(): Promise<FarmOption[] | null> {
  const t = await token();
  try {
    const page = await api.get<PageResponse<FarmOption>>("/api/farm/v1/farms?size=100&sort=name,asc", t);
    return page.content;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/login");
    return null;
  }
}

export async function loadDairySummary(farmId: string, from?: string, to?: string): Promise<DairySummary | null> {
  const t = await token();
  const query = new URLSearchParams({ farmId });
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  try {
    return await api.get<DairySummary>(`/api/farm/v1/dairy/summary?${query}`, t);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/login");
    return null;
  }
}

/** Litres with one decimal; a figure is never shown as "NaN" or blank. */
export function litres(value: number | null | undefined): string {
  return `${(value ?? 0).toLocaleString("en-KE", { maximumFractionDigits: 1 })} L`;
}

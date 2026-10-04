import { redirect } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { readToken } from "@/lib/session";

export type Record_ = Record<string, unknown>;

/** Fetches a single record, sending an expired session to sign-in. */
export async function loadRecord(path: string, id: string): Promise<Record_ | null> {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }
  try {
    return await api.get<Record_>(`${path}/${id}`, token);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      redirect("/login");
    }
    return null;
  }
}

/** Formats a raw API value for display in a table cell or detail row. */
export function display(value: unknown, kind: string): string {
  if (value === null || value === undefined || value === "") return "—";
  if (kind === "bool") return value ? "Yes" : "No";
  if (kind === "ts" && typeof value === "string") {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? value : parsed.toISOString().slice(0, 16).replace("T", " ");
  }
  if (typeof value === "number") return value.toLocaleString();
  const text = String(value);
  // GHOST_ATTENDANCE, POTATO, ACTIVE: codes read as words ("Ghost attendance"). Short codes (KES) and ids are left alone.
  if (/^[A-Z]{4,}(?:_[A-Z]+)*$/.test(text)) {
    const words = text.toLowerCase().replaceAll("_", " ");
    return words.charAt(0).toUpperCase() + words.slice(1);
  }
  return text;
}

/** A raw database id: meaningful to a join, noise to a person reading a record. */
export const isUuid = (value: unknown): boolean =>
  typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);

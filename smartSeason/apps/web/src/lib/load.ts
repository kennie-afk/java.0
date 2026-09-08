import { redirect } from "next/navigation";
import { api, ApiError, type PageResponse } from "@/lib/api";
import { readToken } from "@/lib/session";

/** One screenful. Small enough to scan, large enough to stay useful. */
export const PAGE_SIZE = 25;

export interface Collection<T> {
  rows: T[];
  failed: boolean;
  page: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Reads the `sort` query parameter, but only honours it when the field is one
 * the entity actually has: the services return 500 for an unknown sort field,
 * and a hand-edited URL should not look like an outage.
 */
export function sortParam(
  value: string | string[] | undefined,
  allowed: string[]
): string | null {
  const raw = Array.isArray(value) ? value[0] : value;
  if (!raw) return null;
  const [field, direction] = raw.split(",");
  if (!allowed.includes(field)) return null;
  return `${field},${direction === "desc" ? "desc" : "asc"}`;
}

/** Reads the `page` query parameter, clamped to a sane 0-based page number. */
export function pageParam(value: string | string[] | undefined): number {
  const raw = Array.isArray(value) ? value[0] : value;
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 0;
}

/**
 * Fetches one page of a collection for a dashboard screen.
 *
 * A 401 means the session is over, not that the service is down, so it sends
 * the user to sign in rather than reporting a false outage. Every other error
 * is reported as an outage by the caller.
 */
export async function loadCollection<T>(
  path: string,
  page = 0,
  sort: string | null = null
): Promise<Collection<T>> {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }

  const separator = path.includes("?") ? "&" : "?";
  const sortQuery = sort ? `&sort=${encodeURIComponent(sort)}` : "";
  const url = `${path}${separator}page=${page}&size=${PAGE_SIZE}${sortQuery}`;

  try {
    const result = await api.get<PageResponse<T>>(url, token);
    return {
      rows: result.content,
      failed: false,
      page: result.page,
      totalElements: result.totalElements,
      totalPages: result.totalPages
    };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      redirect("/login");
    }
    return { rows: [], failed: true, page: 0, totalElements: 0, totalPages: 0 };
  }
}

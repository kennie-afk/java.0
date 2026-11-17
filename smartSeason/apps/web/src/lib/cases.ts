import { api, ApiError } from "@/lib/api";
import { readToken } from "@/lib/session";

const BASE = "/api/agronomy/v1/cases";

export interface CaseView {
  id: string;
  cropCode: string | null;
  county: string | null;
  question: string;
  status: "OPEN" | "ASSIGNED" | "ANSWERED" | "CLOSED";
  priority: number;
  overdue: boolean;
  dueAt: string;
  createdAt: string;
  raisedByName: string | null;
  assignedTo: string | null;
  assignedToName: string | null;
  aiSource: string | null;
  aiConfidence: number | null;
  aiSeverity: string | null;
  reason: string;
  photoCount: number;
  unread: boolean;
}

export interface CaseMessage {
  id: string;
  authorId: string;
  authorName: string | null;
  authorRole: string;
  body: string;
  at: string;
}

export interface CaseDetail {
  summary: CaseView;
  fullQuestion: string;
  aiSummary: string | null;
  plotId: string | null;
  seasonId: string | null;
  advisoryId: string | null;
  similarOpen: number;
  messages: CaseMessage[];
  photos: { id: string; contentType: string; sizeBytes: number }[];
  canClaim: boolean;
  canReply: boolean;
  canResolve: boolean;
}

export interface CasePage {
  items: CaseView[];
  next: string | null;
}

export interface CaseSummary {
  unassigned: number;
  mine: number;
  overdue: number;
  attention: number;
}

export interface CoverageProfile {
  userId: string;
  fullName: string | null;
  email: string | null;
  counties: string[];
  maxOpen: number;
  active: boolean;
  openCases: number;
}

export const PRIORITY_LABEL: Record<number, string> = { 0: "Info", 1: "Low", 2: "Medium", 3: "Urgent", 4: "Critical" };

/** A failed read becomes an empty result: a case screen should still render, not crash the shell. */
async function safe<T>(read: () => Promise<T>, fallback: T): Promise<T> {
  try {
    return await read();
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403 || error.status === 404)) {
      return fallback;
    }
    throw error;
  }
}

function paged(path: string, params: Record<string, string | undefined>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) if (value) query.set(key, value);
  const text = query.toString();
  return `${BASE}${path}${text ? `?${text}` : ""}`;
}

export async function loadMine(whole: boolean, cursor?: string): Promise<CasePage> {
  const token = await readToken();
  return safe(
    () => api.get<CasePage>(paged("/mine", { scope: whole ? "ORG" : "MINE", cursor, size: "20" }), token),
    { items: [], next: null }
  );
}

export async function loadQueue(scope: "UNASSIGNED" | "MINE" | "ANSWERED", cursor?: string): Promise<CasePage> {
  const token = await readToken();
  return safe(() => api.get<CasePage>(paged("/queue", { scope, cursor, size: "20" }), token), { items: [], next: null });
}

export async function loadCase(id: string): Promise<CaseDetail | null> {
  const token = await readToken();
  return safe(() => api.get<CaseDetail>(`${BASE}/${id}`, token), null);
}

export async function loadSummary(): Promise<CaseSummary> {
  const token = await readToken();
  return safe(() => api.get<CaseSummary>(`${BASE}/summary`, token), { unassigned: 0, mine: 0, overdue: 0, attention: 0 });
}

export async function loadMyCoverage(): Promise<CoverageProfile | null> {
  const token = await readToken();
  return safe(() => api.get<CoverageProfile>(`${BASE}/profile`, token), null);
}

export async function loadCoverage(): Promise<CoverageProfile[]> {
  const token = await readToken();
  return safe(() => api.get<CoverageProfile[]>(`${BASE}/profiles`, token), []);
}

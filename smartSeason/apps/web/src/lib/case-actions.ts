"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { api, ApiError } from "@/lib/api";
import { readToken } from "@/lib/session";

const BASE = "/api/agronomy/v1/cases";

import type { CaseActionState } from "@/lib/case-state";

function fail(error: unknown): CaseActionState {
  if (error instanceof ApiError) return { error: error.problem?.detail ?? error.message };
  return { error: "Something went wrong. Try again." };
}

const MAX_PHOTO_BYTES = 900_000;

/** Sends the farmer's question, with the AI's answer and photographs, to an agronomist. */
export async function raiseCase(_prev: CaseActionState, form: FormData): Promise<CaseActionState> {
  const token = await readToken();
  if (!token) redirect("/login");

  const text = (name: string) => String(form.get(name) ?? "").trim();
  const photos: { mediaType: string; base64: string }[] = [];
  for (const entry of form.getAll("photos")) {
    if (!(entry instanceof File) || entry.size === 0) continue;
    if (entry.size > MAX_PHOTO_BYTES) return { error: `${entry.name} is too large to send. Choose a smaller photograph.` };
    photos.push({ mediaType: entry.type || "image/jpeg", base64: Buffer.from(await entry.arrayBuffer()).toString("base64") });
  }
  const confidence = Number(text("aiConfidence"));
  const question = text("question");
  if (question.length < 3) return { error: "Tell the agronomist what you are seeing." };

  let id: string;
  try {
    const created = await api.post<{ summary: { id: string } }>(
      BASE,
      {
        cropCode: text("cropCode") || null,
        county: text("county") || null,
        question,
        reason: text("reason") || "REQUESTED",
        aiSource: text("aiSource") || null,
        aiSummary: text("aiSummary") || null,
        aiConfidence: Number.isFinite(confidence) && text("aiConfidence") !== "" ? confidence : null,
        aiSeverity: text("aiSeverity") || null,
        photos: photos.slice(0, 3)
      },
      token
    );
    id = created.summary.id;
  } catch (error) {
    return fail(error);
  }
  revalidatePath("/cases");
  redirect(`/cases/${id}`);
}

export async function replyToCase(_prev: CaseActionState, form: FormData): Promise<CaseActionState> {
  const token = await readToken();
  if (!token) redirect("/login");
  const id = String(form.get("id") ?? "");
  const body = String(form.get("body") ?? "").trim();
  if (!body) return { error: "Write a message first." };
  try {
    await api.post(`${BASE}/${id}/messages`, { body }, token);
  } catch (error) {
    return fail(error);
  }
  revalidatePath(`/cases/${id}`);
  revalidatePath("/queue");
  return { error: null, done: true };
}

export async function resolveCase(_prev: CaseActionState, form: FormData): Promise<CaseActionState> {
  const token = await readToken();
  if (!token) redirect("/login");
  const id = String(form.get("id") ?? "");
  const title = String(form.get("title") ?? "").trim();
  const body = String(form.get("body") ?? "").trim();
  if (!title || !body) return { error: "Give the advisory a title and the advice itself." };
  try {
    await api.post(`${BASE}/${id}/resolve`, { title, body, severity: String(form.get("severity") ?? "MEDIUM") }, token);
  } catch (error) {
    return fail(error);
  }
  revalidatePath(`/cases/${id}`);
  revalidatePath("/queue");
  return { error: null, done: true };
}

export async function claimCase(_prev: CaseActionState, form: FormData): Promise<CaseActionState> {
  const token = await readToken();
  if (!token) redirect("/login");
  const id = String(form.get("id") ?? "");
  try {
    await api.post(`${BASE}/${id}/claim`, {}, token);
  } catch (error) {
    return fail(error);
  }
  revalidatePath(`/cases/${id}`);
  revalidatePath("/queue");
  return { error: null, done: true };
}

export async function closeCase(_prev: CaseActionState, form: FormData): Promise<CaseActionState> {
  const token = await readToken();
  if (!token) redirect("/login");
  const id = String(form.get("id") ?? "");
  try {
    await api.post(`${BASE}/${id}/close`, {}, token);
  } catch (error) {
    return fail(error);
  }
  revalidatePath(`/cases/${id}`);
  revalidatePath("/cases");
  revalidatePath("/queue");
  return { error: null, done: true };
}

export async function saveCoverage(_prev: CaseActionState, form: FormData): Promise<CaseActionState> {
  const token = await readToken();
  if (!token) redirect("/login");
  const counties = String(form.get("counties") ?? "")
    .split(",")
    .map((county) => county.trim())
    .filter(Boolean);
  const maxOpen = Number(form.get("maxOpen"));
  if (!Number.isInteger(maxOpen) || maxOpen < 1 || maxOpen > 500) return { error: "Open-case limit must be a whole number from 1 to 500." };
  try {
    await api.put(`${BASE}/profile`, { counties, maxOpen, active: form.get("active") === "on" }, token);
  } catch (error) {
    return fail(error);
  }
  revalidatePath("/queue");
  return { error: null, done: true };
}

"use server";

import { api, ApiError } from "@/lib/api";
import { readToken } from "@/lib/session";
import type { Diagnosis, DiagnosisState } from "@/lib/advisor-types";

const BASE = "/api/agronomy/v1/intelligence";

/** Roughly 5MB per image once base64 expansion is accounted for. */
const MAX_BASE64 = 7_000_000;

export async function diagnose(
  _prev: DiagnosisState,
  form: FormData
): Promise<DiagnosisState> {
  const crop = String(form.get("cropCode") ?? "MAIZE");
  const symptoms = String(form.get("symptoms") ?? "").trim();
  const county = String(form.get("county") ?? "").trim();
  const growthStage = String(form.get("growthStage") ?? "").trim();

  const images: { mediaType: string; base64: string }[] = [];
  for (const entry of form.getAll("photos")) {
    if (!(entry instanceof File) || entry.size === 0) continue;
    if (!entry.type.startsWith("image/")) {
      return { result: null, error: "Only images can be attached", crop, symptoms, imageCount: 0 };
    }
    const base64 = Buffer.from(await entry.arrayBuffer()).toString("base64");
    if (base64.length > MAX_BASE64) {
      return {
        result: null,
        error: `${entry.name} is larger than 5MB`,
        crop,
        symptoms,
        imageCount: 0
      };
    }
    images.push({ mediaType: entry.type, base64 });
  }

  if (!symptoms && images.length === 0) {
    return {
      result: null,
      error: "Describe what you can see, attach a photograph, or both",
      crop,
      symptoms,
      imageCount: 0
    };
  }

  const token = await readToken();
  try {
    const result = await api.post<Diagnosis>(
      `${BASE}/diagnose`,
      {
        cropCode: crop,
        symptoms: symptoms || null,
        images: images.length ? images : null,
        county: county || null,
        growthStage: growthStage || null
      },
      token
    );
    return { result, error: null, crop, symptoms, imageCount: images.length };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? (error.problem?.detail ?? "That request was rejected")
        : "agronomy-service is not reachable";
    return { result: null, error: message, crop, symptoms, imageCount: images.length };
  }
}

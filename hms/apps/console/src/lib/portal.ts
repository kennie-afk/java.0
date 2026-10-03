"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/lib/api";

/** Calls the portal API through the same-origin proxy. A 401 sends the patient to sign in. */
export async function papi<T = unknown>(path: string, init?: { method?: string; body?: unknown }): Promise<T> {
  const res = await fetch(`/api/portal${path}`, {
    method: init?.method ?? "GET",
    headers: init?.body !== undefined ? { "Content-Type": "application/json" } : undefined,
    body: init?.body !== undefined ? JSON.stringify(init.body) : undefined
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (res.status === 401 && typeof window !== "undefined" && !window.location.pathname.startsWith("/portal/login")) window.location.href = "/portal/login";
  if (!res.ok) throw new ApiError(res.status, data ?? {});
  return data as T;
}

export function usePortal<T>(path: string) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await papi<T>(path));
      setError(null);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setLoading(false);
    }
  }, [path]);
  useEffect(() => { void load(); }, [load]);
  return { data, error, loading, reload: load };
}

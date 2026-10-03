"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export class ApiError extends Error {
  status: number;
  code: string;
  fields?: Record<string, string>;
  body: Record<string, unknown>;
  constructor(status: number, body: Record<string, unknown>) {
    super(typeof body.detail === "string" ? body.detail : "Something went wrong.");
    this.status = status;
    this.code = typeof body.code === "string" ? body.code : "error";
    this.fields = body.fields as Record<string, string> | undefined;
    this.body = body;
  }
}

export type Slice<T> = { data: T[]; nextCursor?: string };

export async function api<T = unknown>(path: string, init?: { method?: string; body?: unknown; headers?: Record<string, string> }): Promise<T> {
  const res = await fetch(`/api${path}`, {
    method: init?.method ?? "GET",
    headers: { ...(init?.body !== undefined ? { "Content-Type": "application/json" } : {}), ...init?.headers },
    body: init?.body !== undefined ? JSON.stringify(init.body) : undefined
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (typeof window !== "undefined" && (init?.method ?? "GET") === "GET") {
    // The service worker marks a record it served from its saved copy; say so, so nobody mistakes it for live data.
    const copy = res.headers.get("x-offline-copy");
    window.dispatchEvent(new CustomEvent("hms-offline-copy", { detail: copy ? Number(copy) : null }));
  }
  if (res.status === 401 && typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
    window.location.href = "/login";
  }
  if (!res.ok) throw new ApiError(res.status, data ?? {});
  return data as T;
}

export const post = <T = unknown>(path: string, body?: unknown) => api<T>(path, { method: "POST", body: body ?? {} });
export const put = <T = unknown>(path: string, body?: unknown) => api<T>(path, { method: "PUT", body: body ?? {} });
export const del = <T = unknown>(path: string) => api<T>(path, { method: "DELETE" });

/** Loads one resource; pass null to skip. */
export function useFetch<T>(path: string | null, headers?: Record<string, string>) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(path !== null);
  const headersRef = useRef(headers);
  headersRef.current = headers;
  const load = useCallback(async () => {
    if (path === null) return;
    setLoading(true);
    try {
      setData(await api<T>(path, { headers: headersRef.current }));
      setError(null);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setLoading(false);
    }
  }, [path]);
  useEffect(() => {
    setData(null);
    void load();
  }, [load]);
  return { data, error, loading, reload: load };
}

/** Keyset-paged list: never loads more than one page at a time; "more" appends the next. */
export function usePaged<T>(path: string | null) {
  const [items, setItems] = useState<T[]>([]);
  const [next, setNext] = useState<string | undefined>();
  const [loading, setLoading] = useState(path !== null);
  const [error, setError] = useState<ApiError | null>(null);
  const seq = useRef(0);
  const fetchPage = useCallback(
    async (cursor?: string) => {
      if (path === null) return;
      const mine = ++seq.current;
      setLoading(true);
      try {
        const url = path + (path.includes("?") ? "&" : "?") + (cursor ? `cursor=${encodeURIComponent(cursor)}&` : "") + "limit=25";
        const page = await api<Slice<T>>(url);
        if (mine !== seq.current) return;
        setItems((prev) => (cursor ? [...prev, ...page.data] : page.data));
        setNext(page.nextCursor);
        setError(null);
      } catch (e) {
        if (mine === seq.current) setError(e as ApiError);
      } finally {
        if (mine === seq.current) setLoading(false);
      }
    },
    [path]
  );
  useEffect(() => {
    setItems([]);
    setNext(undefined);
    void fetchPage();
  }, [fetchPage]);
  return { items, loading, error, more: next ? () => fetchPage(next) : null, reload: () => fetchPage() };
}

/** The browser's side: one function per kind of call, always through the console's own server. */
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

export async function call<T = unknown>(
  op: string,
  options: { query?: Record<string, string>; body?: unknown; method?: "GET" | "POST" } = {}
): Promise<T> {
  const method = options.method ?? (options.body === undefined ? "GET" : "POST");
  const qs = options.query ? "?" + new URLSearchParams(options.query).toString() : "";
  const res = await fetch(`/api/office/${op}${qs}`, {
    method,
    headers: { "content-type": "application/json", "x-office-csrf": "1" },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    cache: "no-store"
  });
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (res.status === 401 && typeof window !== "undefined" && !location.pathname.startsWith("/signin")) {
    location.assign("/signin");
  }
  if (!res.ok) {
    throw new ApiError(res.status, json?.error ?? "error", json?.message ?? json?.error ?? `The server answered ${res.status}.`);
  }
  return json as T;
}

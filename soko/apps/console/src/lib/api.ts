import { readSession } from "@/lib/session";

const API = process.env.SOKO_API_URL ?? "http://127.0.0.1:8090";

export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit, token?: string): Promise<T> {
  const bearer = token ?? (await readSession())?.token;

  const response = await fetch(`${API}${path}`, {
    ...init,
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
      ...(init?.headers ?? {})
    }
  });

  if (!response.ok) {
    let detail = response.statusText;
    try {
      const body = (await response.json()) as { detail?: string };
      detail = body.detail ?? detail;
    } catch {
      detail = response.statusText;
    }
    throw new ApiError(response.status, detail);
  }

  return (await response.json()) as T;
}

export interface LoginResult {
  accessToken: string;
  fullName: string;
  role: string;
  organisation: string;
  expiresInSeconds: number;
}

export interface RegisterInput {
  organisationName: string;
  fullName: string;
  email: string;
  password: string;
}

const json = (body: unknown): RequestInit => ({
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body)
});

export const api = {
  get: <T>(path: string) => request<T>(path),
  login: (email: string, password: string) =>
    request<LoginResult>("/v1/auth/login", json({ email, password }), ""),
  register: (input: RegisterInput) =>
    request<LoginResult>("/v1/auth/register", json(input), ""),
  forgot: (email: string) =>
    request<{ detail: string }>("/v1/auth/forgot", json({ email }), "")
};

export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) {
      return "That session has expired. Sign out on the left, then sign in again.";
    }
    return error.message;
  }
  if (error instanceof Error && error.message.includes("fetch failed")) {
    return `The Soko API is not reachable at ${API}.`;
  }
  return error instanceof Error ? error.message : "Something went wrong.";
}

export function ksh(cents: number): string {
  return `KSh ${(cents / 100).toLocaleString("en-KE", { maximumFractionDigits: 0 })}`;
}

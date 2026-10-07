import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, post } from "./api";

const reply = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(body === null ? "" : JSON.stringify(body), { status, headers });

let events: CustomEvent[];
let win: { location: { pathname: string; href: string }; dispatchEvent: (e: Event) => boolean };

beforeEach(() => {
  events = [];
  win = { location: { pathname: "/patients", href: "/patients" }, dispatchEvent: (e) => { events.push(e as CustomEvent); return true; } };
  vi.stubGlobal("window", win);
  vi.stubGlobal("CustomEvent", class { type: string; detail: unknown; constructor(t: string, i?: { detail?: unknown }) { this.type = t; this.detail = i?.detail; } });
});
afterEach(() => vi.unstubAllGlobals());

const failure = async () => { try { await api("/v1/x"); } catch (x) { return x as ApiError; } throw new Error("expected a failure"); };

describe("api client", () => {
  it("returns parsed JSON and sends a JSON body with the right method", async () => {
    const f = vi.fn().mockResolvedValue(reply(200, { ok: 1 }));
    vi.stubGlobal("fetch", f);
    expect(await post("/v1/x", { a: 1 })).toEqual({ ok: 1 });
    const [url, init] = f.mock.calls[0];
    expect(url).toBe("/api/v1/x");
    expect(init.method).toBe("POST");
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(init.body).toBe('{"a":1}');
  });

  it("turns a problem response into an ApiError with code, detail and field errors", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(reply(400, { code: "validation", detail: "Check the form.", fields: { email: "bad" } })));
    const e = await failure();
    expect(e).toBeInstanceOf(ApiError);
    expect(e.status).toBe(400);
    expect(e.code).toBe("validation");
    expect(e.message).toBe("Check the form.");
    expect(e.fields).toEqual({ email: "bad" });
  });

  it("gives a plain message and the code 'error' when the body has none", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(reply(500, null)));
    const e = await failure();
    expect(e.code).toBe("error");
    expect(e.message).toBe("Something went wrong.");
  });

  it("sends the person to the login page on 401, but not when already there", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation(async () => reply(401, { code: "unauthorized" })));
    await api("/v1/x").catch(() => {});
    expect(win.location.href).toBe("/login");
    win.location.pathname = "/login";
    win.location.href = "stay";
    await api("/v1/x").catch(() => {});
    expect(win.location.href).toBe("stay");
  });

  it("announces when the service worker answered from its saved copy", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(reply(200, {}, { "x-offline-copy": "1700000000000" })));
    await api("/v1/x");
    expect(events[0].type).toBe("hms-offline-copy");
    expect(events[0].detail).toBe(1700000000000);
  });
});

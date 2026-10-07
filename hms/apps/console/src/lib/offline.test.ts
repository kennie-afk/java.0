import "fake-indexeddb/auto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import { discard, flush, listQueued, postQueued, queueable, retry } from "./offline";

const ENC = "11111111-1111-4111-8111-111111111111";
const VITALS = `/v1/clinical/encounters/${ENC}/vitals`;
const NOTES = `/v1/clinical/encounters/${ENC}/notes`;

const reply = (status: number, body: unknown = {}) => new Response(JSON.stringify(body), { status });
let win: { location: { pathname: string; href: string }; dispatchEvent: () => boolean };
let online: boolean;

beforeEach(async () => {
  online = true;
  win = { location: { pathname: "/encounters/x", href: "/encounters/x" }, dispatchEvent: () => true };
  vi.stubGlobal("window", win);
  vi.stubGlobal("Event", class { constructor(public type: string) {} });
  vi.stubGlobal("navigator", { get onLine() { return online; } });
  for (const q of await listQueued()) await discard(q.id);
});
afterEach(() => vi.unstubAllGlobals());

describe("what may be queued", () => {
  it("allows bedside capture only", () => {
    expect(queueable(VITALS)).toBe(true);
    expect(queueable(NOTES)).toBe(true);
    expect(queueable("/v1/billing/invoices")).toBe(false);
    expect(queueable("/v1/pharmacy/stock/adjustments")).toBe(false);
    expect(queueable(`/v1/clinical/encounters/${ENC}/orders`)).toBe(false);
  });
  it("refuses to queue a write that needs a live decision", async () => {
    await expect(postQueued("/v1/billing/invoices", {}, "x")).rejects.toThrow(/cannot be kept/);
  });
});

describe("queue and replay", () => {
  it("keeps writes made offline in order, each with its own idempotency key, and sends them oldest first", async () => {
    online = false;
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const a = await postQueued(VITALS, { n: 1 }, "one");
    await new Promise((r) => setTimeout(r, 3));
    const b = await postQueued(NOTES, { n: 2 }, "two");
    expect(a.queued && b.queued).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
    const queued = await listQueued();
    expect(queued.map((q) => q.label)).toEqual(["one", "two"]);
    expect(new Set(queued.map((q) => q.id)).size).toBe(2);

    online = true;
    fetchMock.mockImplementation(async () => reply(201));
    await flush();
    expect(fetchMock.mock.calls.map((c) => JSON.parse(c[1].body).n)).toEqual([1, 2]);
    // The key sent is the queued id, so a resend after a dropped response is recorded once by the server.
    expect(fetchMock.mock.calls.map((c) => c[1].headers["Idempotency-Key"])).toEqual(queued.map((q) => q.id));
    expect(await listQueued()).toEqual([]);
  });

  it("queues when the network fails mid-send and resends under the same key", async () => {
    const fetchMock = vi.fn().mockRejectedValueOnce(new TypeError("network"));
    vi.stubGlobal("fetch", fetchMock);
    const r = await postQueued(VITALS, { n: 1 }, "v");
    expect(r.queued).toBe(true);
    const [first] = await listQueued();
    expect(fetchMock.mock.calls[0][1].headers["Idempotency-Key"]).toBe(first.id);
    fetchMock.mockImplementation(async () => reply(201));
    await flush();
    expect(fetchMock.mock.calls[1][1].headers["Idempotency-Key"]).toBe(first.id);
  });

  it("throws a refusal at once instead of queueing it", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(reply(409, { code: "encounter_closed", detail: "Closed." })));
    await expect(postQueued(VITALS, {}, "v")).rejects.toBeInstanceOf(ApiError);
    expect(await listQueued()).toEqual([]);
  });

  it("stops at the first unreachable server so order is kept", async () => {
    online = false;
    vi.stubGlobal("fetch", vi.fn());
    await postQueued(VITALS, { n: 1 }, "one");
    await new Promise((r) => setTimeout(r, 3));
    await postQueued(VITALS, { n: 2 }, "two");
    online = true;
    const f = vi.fn().mockRejectedValue(new TypeError("down"));
    vi.stubGlobal("fetch", f);
    await flush();
    expect(f).toHaveBeenCalledTimes(1);
    expect((await listQueued()).length).toBe(2);
  });

  it("marks a refused write FAILED, keeps it, and lets it be retried", async () => {
    online = false;
    vi.stubGlobal("fetch", vi.fn());
    await postQueued(VITALS, { n: 1 }, "one");
    online = true;
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(reply(422, { detail: "Pulse out of range." })));
    await flush();
    const [q] = await listQueued();
    expect(q.status).toBe("FAILED");
    expect(q.error).toBe("Pulse out of range.");
    await retry(q.id);
    expect((await listQueued())[0].status).toBe("PENDING");
  });

  it("backs off on a server error but keeps the write pending", async () => {
    online = false;
    vi.stubGlobal("fetch", vi.fn());
    await postQueued(VITALS, { n: 1 }, "one");
    online = true;
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(reply(503)));
    await flush();
    const [q] = await listQueued();
    expect(q.status).toBe("PENDING");
    expect(q.attempts).toBe(1);
  });
});

describe("token expiry", () => {
  it("keeps every queued write when the access token has expired (401) and stops replaying", async () => {
    online = false;
    vi.stubGlobal("fetch", vi.fn());
    await postQueued(VITALS, { n: 1 }, "one");
    await new Promise((r) => setTimeout(r, 3));
    await postQueued(NOTES, { n: 2 }, "two");
    online = true;
    const f = vi.fn().mockResolvedValue(reply(401, { code: "unauthorized" }));
    vi.stubGlobal("fetch", f);
    await flush();
    expect(f).toHaveBeenCalledTimes(1);
    const left = await listQueued();
    expect(left.map((q) => q.status)).toEqual(["PENDING", "PENDING"]);
  });
});

import { describe, expect, it } from "vitest";
import { build, OPS } from "../src/lib/routes";

const bases = { identity: "http://identity:8081", core: "http://core:8083", sync: "http://sync:8082" };
const q = (s = "") => new URLSearchParams(s);

describe("the fixed list of calls the console may make", () => {
  it("maps a known operation to its upstream URL", () => {
    const r = build("terminals", "GET", q(), undefined, bases);
    expect(r).toMatchObject({ ok: true });
    expect(r.ok && r.url.toString()).toBe("http://identity:8081/v1/admin/terminals");
    const s = build("report.sales", "GET", q("from=2026-10-01&to=2026-10-07&by=terminal&zone=Africa/Nairobi"), undefined, bases);
    expect(s.ok && s.url.toString()).toBe("http://core:8083/v1/admin/reports/sales?from=2026-10-01&to=2026-10-07&by=terminal&zone=Africa%2FNairobi");
  });

  it("refuses an unknown operation, including ones shaped like paths or prototype keys", () => {
    for (const name of ["nope", "../v1/admin/credentials", "v1/admin/credentials", "constructor", "__proto__", "toString", ""]) {
      expect(build(name, "GET", q(), undefined, bases), name).toMatchObject({ ok: false, status: 404 });
    }
  });

  it("holds the method to what the operation is", () => {
    expect(build("terminals", "POST", q(), "{}", bases)).toMatchObject({ ok: false, status: 405 });
    expect(build("staff.create", "GET", q(), undefined, bases)).toMatchObject({ ok: false, status: 405 });
  });

  it("only forwards the query parameters an operation lists, and only well-formed values", () => {
    expect(build("terminals", "GET", q("x=1"), undefined, bases)).toMatchObject({ ok: false, status: 400 });
    expect(build("audit", "GET", q("limit=50"), undefined, bases)).toMatchObject({ ok: true });
    expect(build("audit", "GET", q("limit=abc"), undefined, bases)).toMatchObject({ ok: false, status: 400 });
    expect(build("audit", "GET", q("limit=5000"), undefined, bases)).toMatchObject({ ok: false, status: 400 });
    expect(build("report.sales", "GET", q("from=2026-10-01&to=2026-10-07&by=sku"), undefined, bases)).toMatchObject({ ok: false });
    expect(build("report.sales", "GET", q("from=yesterday&to=2026-10-07"), undefined, bases)).toMatchObject({ ok: false });
    expect(build("report.sales", "GET", q("from=2026-10-01&to=2026-10-07&zone=..%2F..%2Fx"), undefined, bases)).toMatchObject({ ok: false });
  });

  it("validates a path parameter before it goes into a URL", () => {
    expect(build("terminal.status", "POST", q("id=TERM-0123456789ABCDEF0123"), JSON.stringify({ status: "SUSPENDED" }), bases)).toMatchObject({ ok: true });
    for (const id of ["", "../credentials", "TERM-xyz", "TERM-0123456789ABCDEF0123/../x", "term-0123456789abcdef0123"]) {
      expect(build("terminal.status", "POST", q("id=" + encodeURIComponent(id)), "{}", bases), id).toMatchObject({ ok: false, status: 400 });
    }
    const ok = build("staff.status", "POST", q("id=0b1d6c7e-1a2b-4c3d-8e9f-a1b2c3d4e5f6"), JSON.stringify({ status: "ACTIVE" }), bases);
    expect(ok.ok && ok.url.pathname).toBe("/v1/admin/staff/0b1d6c7e-1a2b-4c3d-8e9f-a1b2c3d4e5f6/status");
  });

  it("forwards only the body fields an operation names, as a JSON object", () => {
    const body = JSON.stringify({ branchId: "b", displayName: "N", role: "CASHIER", staffNumber: "1", pin: "4826" });
    expect(build("staff.create", "POST", q(), body, bases)).toMatchObject({ ok: true });
    expect(build("staff.create", "POST", q(), JSON.stringify({ role: "OWNER", tenantId: "other" }), bases)).toMatchObject({ ok: false, error: "unexpected_field_tenantId" });
    for (const bad of [undefined, "", "not json", "[]", "null", "5", "x".repeat(20_000)]) {
      expect(build("staff.create", "POST", q(), bad, bases), String(bad).slice(0, 10)).toMatchObject({ ok: false, status: 400 });
    }
  });

  it("is not configured when its service address is missing", () => {
    expect(build("terminals", "GET", q(), undefined, { ...bases, identity: undefined })).toMatchObject({ ok: false, status: 502 });
  });

  it("never exposes credential management, tenant creation or anything internal", () => {
    const upstream = Object.values(OPS).map((o) => o.path);
    expect(upstream.filter((p) => p.includes("credentials") || p.includes("/internal") || p.endsWith("/tenants"))).toEqual([]);
  });
});

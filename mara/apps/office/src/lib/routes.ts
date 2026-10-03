/**
 * The ONLY calls the console makes to the Mara services. The browser names an operation; this table decides what it
 * means. It is not a proxy: nothing here forwards an arbitrary path, method or query, and every path parameter is
 * validated against a pattern before it is placed in a URL.
 */
export type Service = "identity" | "core" | "sync";

export interface Op {
  service: Service;
  method: "GET" | "POST";
  /** the upstream path; {id} is replaced by the validated `id` parameter */
  path: string;
  /** pattern the `id` must match, when the path has one */
  id?: RegExp;
  /** query parameters passed through, each with the pattern its value must match */
  query?: Record<string, RegExp>;
  /** the JSON body is forwarded only for POST, and only these keys */
  body?: string[];
}

const UUID = /^[0-9a-fA-F-]{36}$/;
const TERMINAL = /^TERM-[0-9A-F]{20}$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export const OPS: Record<string, Op> = {
  branches: { service: "identity", method: "GET", path: "/v1/admin/branches" },
  staff: { service: "identity", method: "GET", path: "/v1/admin/staff" },
  "staff.create": { service: "identity", method: "POST", path: "/v1/admin/staff",
    body: ["branchId", "displayName", "role", "staffNumber", "pin"] },
  "staff.status": { service: "identity", method: "POST", path: "/v1/admin/staff/{id}/status", id: UUID, body: ["status"] },
  terminals: { service: "identity", method: "GET", path: "/v1/admin/terminals" },
  "terminal.status": { service: "identity", method: "POST", path: "/v1/admin/terminals/{id}/status", id: TERMINAL, body: ["status"] },
  "enrolment.issue": { service: "identity", method: "POST", path: "/v1/admin/enrolment-codes", body: ["branchId", "issuedBy"] },
  audit: { service: "identity", method: "GET", path: "/v1/admin/audit", query: { limit: /^\d{1,3}$/ } },
  "report.sales": { service: "core", method: "GET", path: "/v1/admin/reports/sales",
    query: { from: DATE, to: DATE, by: /^(day|terminal|cashier)$/, zone: /^[A-Za-z_]+(\/[A-Za-z_+-]+){0,2}$/ } },
  "core.exceptions": { service: "core", method: "GET", path: "/v1/admin/exceptions", query: { open: /^(true|false)$/, limit: /^\d{1,3}$/ } },
  leases: { service: "core", method: "GET", path: "/v1/admin/fiscal/leases" },
  "sync.exceptions": { service: "sync", method: "GET", path: "/v1/admin/exceptions", query: { open: /^(true|false)$/, limit: /^\d{1,3}$/ } }
};

export type Built = { ok: true; url: URL; body: string | undefined } | { ok: false; status: number; error: string };

/** Turns (operation, params, body) into an upstream request, or says exactly why it refuses to. */
export function build(
  name: string,
  method: string,
  params: URLSearchParams,
  bodyText: string | undefined,
  bases: Record<Service, string | undefined>
): Built {
  const op = Object.hasOwn(OPS, name) ? OPS[name] : undefined;
  if (!op) return { ok: false, status: 404, error: "unknown_operation" };
  if (op.method !== method) return { ok: false, status: 405, error: "method_not_allowed" };
  const base = bases[op.service];
  if (!base) return { ok: false, status: 502, error: "not_configured" };
  let path = op.path;
  if (op.id) {
    const id = params.get("id");
    if (!id || !op.id.test(id)) return { ok: false, status: 400, error: "bad_id" };
    path = path.replace("{id}", id);
  }
  const url = new URL(path, base);
  for (const [key, value] of params) {
    if (key === "id" && op.id) continue;
    const pattern = op.query?.[key];
    if (!pattern || !pattern.test(value)) return { ok: false, status: 400, error: `bad_parameter_${key}` };
    url.searchParams.set(key, value);
  }
  let body: string | undefined;
  if (op.method === "POST") {
    if (bodyText === undefined || bodyText.length > 16_384) return { ok: false, status: 400, error: "bad_body" };
    let parsed: unknown;
    try {
      parsed = JSON.parse(bodyText);
    } catch {
      return { ok: false, status: 400, error: "bad_body" };
    }
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return { ok: false, status: 400, error: "bad_body" };
    const allowed = new Set(op.body ?? []);
    const extra = Object.keys(parsed).filter((k) => !allowed.has(k));
    if (extra.length) return { ok: false, status: 400, error: `unexpected_field_${extra[0]}` };
    body = JSON.stringify(parsed);
  }
  return { ok: true, url, body };
}

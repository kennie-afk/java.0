/**
 * Runtime switches that describe the deployment, not the user.
 *
 * Read on the server at request time, so one image serves a full install and the
 * lean demo alike; nothing here is baked in at build.
 */

/** True on a demo stack: sample data, and every external provider simulated. */
export function demoMode(): boolean {
  return process.env.DEMO_MODE === "true";
}

/**
 * Service slugs that are actually running here, or null for "all of them".
 *
 * The lean demo runs a subset of the 26 services. Without this the menu would
 * offer screens whose backend is not there and every one would be an error page.
 */
export function enabledServices(): string[] | null {
  const raw = (process.env.DEMO_SERVICES ?? "").trim();
  if (!raw) return null;
  return raw.split(",").map((slug) => slug.trim()).filter(Boolean);
}

/** The service slug in an `/api/<slug>/v1/...` path. */
export function serviceOfPath(path: string): string {
  return path.split("/")[2] ?? "";
}

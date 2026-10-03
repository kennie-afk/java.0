/* HMS console service worker.
 *
 * What it does: keeps the app itself (scripts, styles, the pages a person has opened) so the console opens without a
 * connection, and keeps a short-lived copy of the point-of-care records a person has read (queue, patient, encounter,
 * maternal, programme, ward screens) so they can be looked at again offline.
 *
 * What it will not do: cache anything that was sent with an access reason, any imaging study or image (patient pictures stay off the device), anything about billing, claims, staff,
 * reports or audit, or anything of the patient portal; serve a saved record older than 12 hours; or send a write. Writes
 * are queued by the page (lib/offline.ts), never here. Saved records are deleted on sign-out.
 */
const VERSION = "v1";
const STATIC = `hms-static-${VERSION}`;
const PAGES = `hms-pages-${VERSION}`;
const API = `hms-api-${VERSION}`;
const MAX_AGE_MS = 12 * 60 * 60 * 1000;

const READABLE = [
  /^\/api\/session$/,
  /^\/api\/v1\/scheduling\/(queue|appointments|clinics)/,
  /^\/api\/v1\/patients(\/[0-9a-f-]{36})?(\?|$)/,
  /^\/api\/v1\/clinical\/encounters(\/[0-9a-f-]{36})?(\?|$)/,
  /^\/api\/v1\/clinical\/patients\/[0-9a-f-]{36}\/allergies/,
  /^\/api\/v1\/mch\//,
  /^\/api\/v1\/programmes\//,
  /^\/api\/v1\/lab\/orders/,
  /^\/api\/v1\/inpatient\//,
  /^\/api\/v1\/facilities/
];

const NEVER_PAGES = [/^\/portal/, /^\/login/, /^\/setup/, /^\/api\//];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(STATIC).then((c) => c.addAll(["/offline.html"])).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((names) => Promise.all(names.filter((n) => n.startsWith("hms-") && !n.endsWith(`-${VERSION}`)).map((n) => caches.delete(n)))).then(() => self.clients.claim())
  );
});

self.addEventListener("message", (event) => {
  if (event.data === "clear") {
    event.waitUntil(Promise.all([caches.delete(API), caches.delete(PAGES)]));
  }
});

async function stamped(response) {
  const body = await response.clone().blob();
  const headers = new Headers(response.headers);
  headers.set("x-sw-cached-at", String(Date.now()));
  return new Response(body, { status: response.status, statusText: response.statusText, headers });
}

async function networkFirstApi(request) {
  const cache = await caches.open(API);
  try {
    const response = await fetch(request);
    if (response.ok) await cache.put(request.url, await stamped(response));
    return response;
  } catch (err) {
    const hit = await cache.match(request.url);
    if (hit) {
      const at = Number(hit.headers.get("x-sw-cached-at") || 0);
      if (Date.now() - at < MAX_AGE_MS) {
        const headers = new Headers(hit.headers);
        headers.set("x-offline-copy", String(at));
        return new Response(await hit.blob(), { status: hit.status, headers });
      }
      await cache.delete(request.url);
    }
    throw err;
  }
}

async function networkFirstPage(request) {
  const cache = await caches.open(PAGES);
  try {
    const response = await fetch(request);
    const type = response.headers.get("content-type") || "";
    if (response.ok && type.includes("text/html") && !NEVER_PAGES.some((r) => r.test(new URL(request.url).pathname))) await cache.put(request.url, response.clone());
    return response;
  } catch (err) {
    return (await cache.match(request.url)) || (await caches.match("/offline.html")) || Response.error();
  }
}

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/_next/static/")) {
    event.respondWith(caches.open(STATIC).then(async (c) => (await c.match(request)) || fetch(request).then((r) => { if (r.ok) c.put(request, r.clone()); return r; })));
    return;
  }
  if (request.mode === "navigate") {
    event.respondWith(networkFirstPage(request));
    return;
  }
  // A read sent with an access reason is a restricted record: never kept.
  if (request.headers.has("x-access-reason")) return;
  if (READABLE.some((r) => r.test(url.pathname + url.search))) event.respondWith(networkFirstApi(request));
});

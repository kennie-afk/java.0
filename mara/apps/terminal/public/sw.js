/*
 * Minimal offline worker. It does one thing: keep the till loadable with no network.
 *
 *  - install: fetch every app route, and every /_next/static asset those pages name, into
 *    one versioned cache, so a route the operator has not opened yet still works offline.
 *  - fetch:   /_next/static and icons are cache-first (content-hashed); navigations are network-first with the cache as
 *    the fallback, so a reachable server always wins and an unreachable one never blanks
 *    the screen.
 *  - /api/* is never cached. Enrolment and reachability must reflect the real network.
 *
 * It does not sync, queue or replay anything; there is no server to send data to.
 */
const VERSION = "mara-terminal-v1";
const ROUTES = [
  "/", "/sale", "/sale/new", "/sale/tab", "/sale/tender", "/receipt", "/catalogue", "/catalogue/new",
  "/catalogue/edit", "/journal", "/journal/verify", "/enrol", "/settings"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(VERSION);
      const assets = new Set(["/logo-icon.svg", "/manifest.webmanifest"]);
      for (const route of ROUTES) {
        try {
          const res = await fetch(route, { credentials: "same-origin" });
          if (!res.ok) continue;
          const html = await res.clone().text();
          await cache.put(route, res);
          for (const m of html.matchAll(/\/_next\/static\/[^"'\\\s)]+/g)) assets.add(m[0]);
        } catch (_) { /* a route that fails to prefetch is cached on first visit instead */ }
      }
      await Promise.all([...assets].map((a) => cache.add(a).catch(() => undefined)));
      await self.skipWaiting();
    })()
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      for (const key of await caches.keys()) if (key !== VERSION) await caches.delete(key);
      await self.clients.claim();
    })()
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/api/")) return;

  if (req.mode === "navigate") {
    event.respondWith(
      (async () => {
        const cache = await caches.open(VERSION);
        try {
          const res = await fetch(req);
          if (res.ok) cache.put(url.pathname, res.clone());
          return res;
        } catch (_) {
          return (await cache.match(url.pathname)) || (await cache.match("/")) || Response.error();
        }
      })()
    );
    return;
  }

  const immutable = url.pathname.startsWith("/_next/static/") || /\.(svg|png|ico|webmanifest)$/.test(url.pathname);
  event.respondWith(
    (async () => {
      const cache = await caches.open(VERSION);
      if (immutable) {
        const hit = await cache.match(req);
        if (hit) return hit;
      }
      try {
        const res = await fetch(req);
        if (res.ok && immutable) cache.put(req, res.clone());
        return res;
      } catch (_) {
        return (await cache.match(req)) || Response.error();
      }
    })()
  );
});

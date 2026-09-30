"use client";

import { useEffect } from "react";

/** Registers the offline worker. Production builds only: a worker in dev caches stale code. */
export function SwRegister() {
  useEffect(() => {
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/sw.js").catch(() => undefined);
  }, []);
  return null;
}

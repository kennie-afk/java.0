"use client";

import { useEffect } from "react";
import { syncCycle } from "@/lib/sync";

/** Runs the server conversation in the background: on load, on reconnect, and every 30 seconds. */
export function SyncAgent() {
  useEffect(() => {
    let alive = true;
    const run = () => {
      if (alive) void syncCycle().catch(() => undefined);
    };
    const first = setTimeout(run, 1500);
    const every = setInterval(run, 30_000);
    window.addEventListener("online", run);
    return () => {
      alive = false;
      clearTimeout(first);
      clearInterval(every);
      window.removeEventListener("online", run);
    };
  }, []);
  return null;
}

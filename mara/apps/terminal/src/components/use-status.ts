"use client";

import { useEffect, useState } from "react";
import { countItems } from "@/lib/catalogue-store";
import { getHead, getLease } from "@/lib/journal-store";
import type { JournalHead, StoredLease, TerminalIdentity } from "@/lib/records";
import { getIdentity, getSettings } from "@/lib/terminal-store";

export interface TerminalStatus {
  loaded: boolean;
  identity: TerminalIdentity | null;
  currency: string;
  online: boolean;
  identityReachable: boolean | null;
  identityConfigured: boolean | null;
  head: JournalHead | null;
  lease: StoredLease | null;
  items: number;
  storageError: string | null;
}

export function useTerminalStatus(refreshKey = 0): TerminalStatus {
  const [s, setS] = useState<TerminalStatus>({
    loaded: false,
    identity: null,
    currency: "KES",
    online: true,
    identityReachable: null,
    identityConfigured: null,
    head: null,
    lease: null,
    items: 0,
    storageError: null
  });

  useEffect(() => {
    let alive = true;
    const read = async () => {
      try {
        const [identity, settings, head, lease, items] = await Promise.all([
          getIdentity(),
          getSettings(),
          getHead(),
          getLease(),
          countItems()
        ]);
        if (alive) setS((p) => ({ ...p, loaded: true, identity, currency: settings.currency, head, lease, items, storageError: null }));
      } catch (e) {
        if (alive) setS((p) => ({ ...p, loaded: true, storageError: e instanceof Error ? e.message : "local storage unavailable" }));
      }
    };
    const probe = async () => {
      const online = navigator.onLine;
      if (alive) setS((p) => ({ ...p, online }));
      if (!online) {
        if (alive) setS((p) => ({ ...p, identityReachable: false }));
        return;
      }
      try {
        const r = await fetch("/api/identity-status", { cache: "no-store" });
        const j = (await r.json()) as { configured: boolean; reachable: boolean };
        if (alive) setS((p) => ({ ...p, identityReachable: j.reachable, identityConfigured: j.configured }));
      } catch {
        if (alive) setS((p) => ({ ...p, identityReachable: false }));
      }
    };
    void read();
    void probe();
    const onNet = () => void probe();
    window.addEventListener("online", onNet);
    window.addEventListener("offline", onNet);
    return () => {
      alive = false;
      window.removeEventListener("online", onNet);
      window.removeEventListener("offline", onNet);
    };
  }, [refreshKey]);

  return s;
}

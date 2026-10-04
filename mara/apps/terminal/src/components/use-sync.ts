"use client";

import { useEffect, useState } from "react";
import { getSyncState } from "@/lib/sync";
import type { SyncState } from "@/lib/records";

/** The journal's standing with the server, re-read every few seconds so the sidebar and Status page stay true. */
export function useSyncState(): SyncState | null {
  const [state, setState] = useState<SyncState | null>(null);
  useEffect(() => {
    let live = true;
    const read = () => void getSyncState().then((s) => live && setState(s)).catch(() => null);
    read();
    const t = setInterval(read, 5000);
    return () => {
      live = false;
      clearInterval(t);
    };
  }, []);
  return state;
}

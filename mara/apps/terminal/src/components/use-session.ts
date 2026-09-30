"use client";

import { useCallback, useEffect, useState } from "react";
import { getSession, type StaffSession } from "@/lib/staff";

export function useStaffSession(refreshKey = 0): { loaded: boolean; session: StaffSession | null; refresh: () => void } {
  const [state, setState] = useState<{ loaded: boolean; session: StaffSession | null }>({ loaded: false, session: null });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let alive = true;
    void getSession()
      .then((session) => alive && setState({ loaded: true, session }))
      .catch(() => alive && setState({ loaded: true, session: null }));
    return () => {
      alive = false;
    };
  }, [refreshKey, tick]);
  const refresh = useCallback(() => setTick((n) => n + 1), []);
  return { loaded: state.loaded, session: state.session, refresh };
}

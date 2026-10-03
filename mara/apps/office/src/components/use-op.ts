"use client";

import { useCallback, useEffect, useState } from "react";
import { call } from "@/lib/api";

export function useOp<T>(op: string, query?: Record<string, string>, enabled = true) {
  const [state, setState] = useState<{ data: T | null; error: string | null; loading: boolean }>({ data: null, error: null, loading: enabled });
  const key = JSON.stringify(query ?? {});
  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true }));
    try {
      const data = await call<T>(op, { query: JSON.parse(key) });
      setState({ data, error: null, loading: false });
    } catch (e) {
      setState({ data: null, error: e instanceof Error ? e.message : "Something went wrong.", loading: false });
    }
  }, [op, key]);
  useEffect(() => {
    if (enabled) void load();
  }, [enabled, load]);
  return { ...state, reload: load };
}

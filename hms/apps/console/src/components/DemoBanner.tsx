/**
 * A permanent reminder that this console runs on sample data. Shown only when the server
 * starts with HMS_DEMO_MODE=true, so a real deployment carries neither the text nor the element.
 * It is read per request in the root layout through the /api/demo route instead of at build.
 */
"use client";

import { useEffect, useState } from "react";

export function DemoBanner() {
  const [on, setOn] = useState(false);
  useEffect(() => {
    fetch("/api/demo").then((r) => r.json()).then((j: { demo?: boolean }) => setOn(!!j.demo)).catch(() => undefined);
  }, []);
  if (!on) return null;
  return (
    <div
      role="note"
      aria-label="Demo mode"
      className="fixed bottom-3 left-1/2 z-[60] max-w-[calc(100vw-1.5rem)] -translate-x-1/2 rounded-full border border-amber-300 bg-amber-50 px-3.5 py-1.5 text-center whitespace-nowrap text-sm font-semibold text-amber-900 shadow-[var(--shadow-lift)]"
    >
      Demo mode · sample data · M-Pesa <span className="hidden sm:inline">is </span>simulated<span className="hidden sm:inline">, no money moves</span>
    </div>
  );
}

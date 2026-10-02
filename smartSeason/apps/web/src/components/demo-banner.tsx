import { demoMode } from "@/lib/deployment";

/**
 * A small, permanent reminder that this stack is a demo. Rendered only when the
 * server runs with DEMO_MODE=true, so a real deployment carries neither the text
 * nor the element.
 */
export function DemoBanner() {
  if (!demoMode()) return null;
  return (
    <div
      role="note"
      aria-label="Demo mode"
      data-testid="demo-banner"
      className="fixed bottom-2 left-1/2 z-[60] max-w-[calc(100vw-1rem)] -translate-x-1/2 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-2xs font-medium text-amber-900"
    >
      Demo mode · sample data · M-Pesa is simulated, no money moves
    </div>
  );
}

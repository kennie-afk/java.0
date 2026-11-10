import { demoMode } from "@/lib/deployment";

/**
 * A small, permanent reminder that this stack is a demo. Rendered only when the
 * server runs with DEMO_MODE=true, so a real deployment carries neither the text
 * nor the element. Same element as SmartRE's DemoBanner, but in the page flow so it never covers content.
 */
export function DemoBanner() {
  if (!demoMode()) return null;
  return (
    <div
      role="note"
      aria-label="Demo mode"
      data-testid="demo-banner"
      className="mx-auto mt-6 w-fit max-w-full rounded border border-amber-300 bg-amber-50 px-2 py-1 text-center text-2xs font-medium text-amber-900 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-200"
    >
      Demo mode · sample data · M-Pesa is simulated, no money moves
    </div>
  );
}

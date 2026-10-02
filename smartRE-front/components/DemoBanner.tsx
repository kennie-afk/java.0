/**
 * A small, permanent reminder that this build is a demo: every external provider is simulated.
 * Shown only when the image was built with NEXT_PUBLIC_DEMO_MODE=true, so a production build
 * carries neither the text nor the element.
 */
const DEMO_MODE = process.env.NEXT_PUBLIC_DEMO_MODE === 'true'

export default function DemoBanner() {
  if (!DEMO_MODE) return null
  return (
    <div
      role="note"
      aria-label="Demo mode"
      className="fixed bottom-2 left-1/2 z-[60] max-w-[calc(100vw-1rem)] -translate-x-1/2 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-2xs font-medium text-amber-900 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-200"
      data-testid="demo-banner"
    >
      Demo mode · sample data · M-Pesa is simulated, no money moves
    </div>
  )
}

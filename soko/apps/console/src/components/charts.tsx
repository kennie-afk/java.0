import { ksh } from "@/lib/money";

export interface WeekPoint {
  weekStart: string;
  orders: number;
  revenueCents: number;
  marginCents: number;
  cancelled: number;
}

/** Weekly revenue bars with the margin drawn inside each bar. Plain SVG, no dependency. */
export function WeeklyBars({ points }: { points: WeekPoint[] }) {
  if (points.length === 0) return null;
  const width = 1100;
  const height = 170;
  const pad = { top: 8, bottom: 22, left: 4, right: 4 };
  const max = Math.max(...points.map((p) => p.revenueCents), 1);
  const slot = (width - pad.left - pad.right) / points.length;
  const bar = Math.min(54, slot * 0.6);
  const y = (v: number) => pad.top + (height - pad.top - pad.bottom) * (1 - v / max);

  return (
    <figure>
      <svg viewBox={`0 0 ${width} ${height}`} role="img" className="w-full"
        aria-label={`Weekly revenue over ${points.length} weeks, latest ${ksh(points[points.length - 1].revenueCents)}`}>
        <line x1={pad.left} x2={width - pad.right} y1={height - pad.bottom} y2={height - pad.bottom} stroke="var(--color-line)" />
        {points.map((p, i) => {
          const x = pad.left + slot * i + (slot - bar) / 2;
          const date = new Date(p.weekStart);
          return (
            <g key={p.weekStart}>
              <title>{`Week of ${date.toDateString()}: ${p.orders} orders, revenue ${ksh(p.revenueCents)}, margin ${ksh(p.marginCents)}${p.cancelled ? `, ${p.cancelled} cancelled` : ""}`}</title>
              <rect x={x} y={y(p.revenueCents)} width={bar} height={height - pad.bottom - y(p.revenueCents)} rx={2} fill="var(--color-accent-soft)" />
              <rect x={x} y={y(p.marginCents)} width={bar} height={height - pad.bottom - y(p.marginCents)} rx={2} fill="var(--color-accent)" />
              <text x={x + bar / 2} y={height - 7} textAnchor="middle" fontSize="11" fill="var(--color-faint)">
                {date.toLocaleDateString("en-KE", { day: "numeric", month: "short" })}
              </text>
            </g>
          );
        })}
      </svg>
      <figcaption className="mt-1 flex gap-4 text-[0.833rem] text-[var(--color-muted)]">
        <span><span className="mr-1 inline-block h-2 w-2 rounded-sm bg-[var(--color-accent-soft)] align-middle" />Revenue</span>
        <span><span className="mr-1 inline-block h-2 w-2 rounded-sm bg-[var(--color-accent)] align-middle" />Margin</span>
      </figcaption>
    </figure>
  );
}

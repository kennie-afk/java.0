import type { ReactNode } from "react";

export function PageHeader({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <header className="mb-8">
      <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
      {subtitle ? <p className="mt-1 text-sm text-[var(--color-muted)]">{subtitle}</p> : null}
    </header>
  );
}

export function Card({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-5">
      {children}
    </div>
  );
}

export function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card>
      <p className="text-xs font-medium uppercase tracking-wide text-[var(--color-muted)]">{label}</p>
      <p className="mt-2 text-3xl font-semibold tabular-nums">{value}</p>
      {hint ? <p className="mt-1 text-xs text-[var(--color-muted)]">{hint}</p> : null}
    </Card>
  );
}

const TONE: Record<string, string> = {
  ACTIVE: "bg-[var(--color-brand-soft)] text-[var(--color-brand)]",
  OPEN: "bg-[#fdf1e3] text-[var(--color-warn)]",
  CRITICAL: "bg-[#fdeceb] text-[var(--color-danger)]",
  HIGH: "bg-[#fdeceb] text-[var(--color-danger)]",
  MEDIUM: "bg-[#fdf1e3] text-[var(--color-warn)]",
  LOW: "bg-[var(--color-brand-soft)] text-[var(--color-brand)]"
};

export function Badge({ value }: { value: string }) {
  const tone = TONE[value] ?? "bg-[#eef1ef] text-[var(--color-muted)]";
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${tone}`}>
      {value}
    </span>
  );
}

export function EmptyState({ message }: { message: string }) {
  return (
    <Card>
      <p className="text-sm text-[var(--color-muted)]">{message}</p>
    </Card>
  );
}

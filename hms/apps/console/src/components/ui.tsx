"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiError } from "@/lib/api";

export function Page({ title, actions, children, sub }: { title: string; actions?: React.ReactNode; sub?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-4 p-3 sm:p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">{title}</h1>
          {sub && <p className="text-xs text-muted">{sub}</p>}
        </div>
        <div className="flex items-center gap-2">{actions}</div>
      </div>
      {children}
    </div>
  );
}

export function Card({ title, actions, children, pad = true }: { title?: string; actions?: React.ReactNode; children: React.ReactNode; pad?: boolean }) {
  return (
    <section className="rounded-lg border border-line bg-surface">
      {title && (
        <div className="flex items-center justify-between border-b border-line px-3 py-2">
          <h2 className="text-2xs font-semibold uppercase tracking-wide text-muted">{title}</h2>
          <div className="flex items-center gap-2">{actions}</div>
        </div>
      )}
      <div className={pad ? "p-3" : ""}>{children}</div>
    </section>
  );
}

export function Table({ head, children, empty }: { head: string[]; children: React.ReactNode; empty?: string }) {
  const rows = Array.isArray(children) ? children.filter(Boolean).length : children ? 1 : 0;
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-xs">
        <thead>
          <tr className="border-b border-line">
            {head.map((h) => (
              <th key={h} className="px-3 py-1.5 text-2xs font-semibold uppercase tracking-wide text-muted">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
      {rows === 0 && empty && <Empty text={empty} />}
    </div>
  );
}

export const Tr = ({ children, href }: { children: React.ReactNode; href?: string }) => (
  <tr className="border-b border-line last:border-0 hover:bg-raised">{href ? children : children}</tr>
);
export const Td = ({ children, className = "", href }: { children?: React.ReactNode; className?: string; href?: string }) => (
  <td className={`px-3 py-1.5 align-top ${className}`}>{href ? <Link href={href} className="font-medium text-accent hover:underline">{children}</Link> : children}</td>
);

export function Empty({ text }: { text: string }) {
  return <div className="px-3 py-9 text-center text-xs text-muted">{text}</div>;
}

export function Loading() {
  return <div className="px-3 py-9 text-center text-xs text-muted">Loading...</div>;
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  const e = error as ApiError;
  const fields = e.fields ? Object.entries(e.fields).map(([k, v]) => `${k}: ${v}`).join("; ") : "";
  return (
    <div role="alert" className="rounded-md border border-danger bg-danger-soft px-3 py-2 text-xs text-danger">
      {e.message || "Something went wrong."} {fields}
    </div>
  );
}

const tones: Record<string, string> = {
  neutral: "bg-raised text-muted",
  good: "bg-good-soft text-good",
  warn: "bg-warn-soft text-warn",
  danger: "bg-danger-soft text-danger",
  accent: "bg-accent-soft text-accent"
};

export function Badge({ children, tone = "neutral" }: { children: React.ReactNode; tone?: keyof typeof tones }) {
  return <span className={`inline-block rounded-sm px-1.5 py-0.5 text-2xs font-semibold uppercase tracking-wide ${tones[tone]}`}>{children}</span>;
}

const STATUS_TONE: Record<string, keyof typeof tones> = {
  PAID: "good", COMPLETED: "good", READY: "good", VALIDATED: "good", DISCHARGED: "neutral", ACTIVE: "good", AVAILABLE: "good", CLOSED: "neutral",
  PARTIALLY_PAID: "warn", ISSUED: "accent", BOOKED: "accent", CHECKED_IN: "warn", IN_PROGRESS: "accent", OPEN: "accent", ADMITTED: "accent", PENDING: "warn",
  ORDERED: "accent", COLLECTED: "accent", RESULTED: "warn", NEEDS_ATTENTION: "danger", FAILED: "danger", VOID: "danger", CANCELLED: "danger", NO_SHOW: "danger",
  DISABLED: "danger", REVERSED: "danger", WITHDRAWN: "neutral", DRAFT: "neutral", SUBMISSION_STUBBED: "warn", OCCUPIED: "warn", CLEANING: "warn",
  OUT_OF_SERVICE: "danger", EMERGENCY: "danger", PRIORITY: "warn", URGENT: "warn", STAT: "danger"
};

export const Status = ({ value }: { value: string }) => <Badge tone={STATUS_TONE[value] ?? "neutral"}>{value.replaceAll("_", " ")}</Badge>;

export function Button({ children, onClick, type = "button", variant = "primary", disabled, busy, href }: {
  children: React.ReactNode; onClick?: () => void; type?: "button" | "submit"; variant?: "primary" | "secondary" | "danger"; disabled?: boolean; busy?: boolean; href?: string;
}) {
  const cls = {
    primary: "bg-accent text-white hover:bg-[#0a5555] border-accent",
    secondary: "bg-surface text-ink hover:bg-raised border-line",
    danger: "bg-surface text-danger hover:bg-danger-soft border-danger"
  }[variant];
  const base = `inline-flex items-center rounded-md border px-2.5 py-1 text-xs font-semibold disabled:opacity-50 ${cls}`;
  if (href) return <Link href={href} className={base}>{children}</Link>;
  return (
    <button type={type} onClick={onClick} disabled={disabled || busy} className={base}>
      {busy ? "Working..." : children}
    </button>
  );
}

export function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return (
    <label className="block space-y-1">
      <span className="text-2xs font-semibold uppercase tracking-wide text-muted">{label}</span>
      {children}
      {hint && <span className="block text-2xs text-faint">{hint}</span>}
    </label>
  );
}

const control = "w-full rounded-md border border-line bg-surface px-2 py-1 text-xs text-ink placeholder:text-faint";
export const Input = (p: React.InputHTMLAttributes<HTMLInputElement>) => <input {...p} className={`${control} ${p.className ?? ""}`} />;
export const Select = (p: React.SelectHTMLAttributes<HTMLSelectElement>) => <select {...p} className={`${control} ${p.className ?? ""}`} />;
export const Textarea = (p: React.TextareaHTMLAttributes<HTMLTextAreaElement>) => <textarea rows={3} {...p} className={`${control} ${p.className ?? ""}`} />;

export function Grid({ cols = 3, children }: { cols?: 2 | 3 | 4; children: React.ReactNode }) {
  const c = { 2: "sm:grid-cols-2", 3: "sm:grid-cols-3", 4: "sm:grid-cols-4" }[cols];
  return <div className={`grid grid-cols-1 gap-3 ${c}`}>{children}</div>;
}

export function Stat({ label, value, tone }: { label: string; value: React.ReactNode; tone?: "warn" | "danger" }) {
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-2">
      <div className="text-2xs font-semibold uppercase tracking-wide text-muted">{label}</div>
      <div className={`text-base font-semibold tabular-nums ${tone === "danger" ? "text-danger" : tone === "warn" ? "text-warn" : ""}`}>{value}</div>
    </div>
  );
}

export function Tabs({ tabs, value, onChange }: { tabs: { key: string; label: string }[]; value: string; onChange: (k: string) => void }) {
  return (
    <div className="flex gap-1 border-b border-line">
      {tabs.map((t) => (
        <button key={t.key} type="button" onClick={() => onChange(t.key)}
          className={`-mb-px border-b-2 px-3 py-1.5 text-xs font-semibold ${value === t.key ? "border-accent text-accent" : "border-transparent text-muted hover:text-ink"}`}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

export function More({ onMore, loading }: { onMore: (() => void) | null; loading: boolean }) {
  if (!onMore) return null;
  return <div className="p-2 text-center"><Button variant="secondary" onClick={onMore} busy={loading}>Load more</Button></div>;
}

/** Delete-style actions confirm in place, never with a blocking browser dialog. */
export function Confirm({ label, prompt, onConfirm, variant = "danger", needsReason, minReason = 5 }: {
  label: string; prompt: string; onConfirm: (reason: string) => Promise<unknown>; variant?: "danger" | "secondary"; needsReason?: boolean; minReason?: number;
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  if (!open) return <Button variant={variant} onClick={() => setOpen(true)}>{label}</Button>;
  return (
    <div className="space-y-2 rounded-md border border-line bg-raised p-2">
      <div className="text-xs">{prompt}</div>
      {needsReason && <Input placeholder="Reason" value={reason} onChange={(e) => setReason(e.target.value)} />}
      <ErrorNote error={error} />
      <div className="flex gap-2">
        <Button variant={variant} busy={busy} disabled={needsReason && reason.trim().length < minReason}
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              await onConfirm(reason.trim());
              setOpen(false);
              setReason("");
            } catch (e) {
              setError(e);
            } finally {
              setBusy(false);
            }
          }}>
          Confirm
        </Button>
        <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
      </div>
    </div>
  );
}

/** Wraps a submit handler with busy and error state. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const run = async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true);
    setError(null);
    try {
      return await fn();
    } catch (e) {
      setError(e);
      return undefined;
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, run, setError };
}

export function KV({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div>
      <div className="text-2xs font-semibold uppercase tracking-wide text-muted">{k}</div>
      <div className="text-xs">{v || <span className="text-faint">-</span>}</div>
    </div>
  );
}

"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { Icon, type IconName } from "@/components/icons";

export function Page({ title, actions, children, sub, eyebrow }: { title: string; actions?: React.ReactNode; sub?: string; eyebrow?: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-[1680px] space-y-5 px-4 pb-16 pt-5 sm:px-6 lg:px-8 lg:pt-7">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0 max-w-3xl">
          {eyebrow && <p className="mb-0.5 text-xs font-semibold uppercase tracking-[0.07em] text-accent">{eyebrow}</p>}
          <h1 className="text-xl font-semibold leading-tight tracking-[-0.01em] sm:text-2xl">{title}</h1>
          {sub && <p className="mt-1 text-sm leading-relaxed text-muted">{sub}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </header>
      {children}
    </div>
  );
}

export function Card({ title, actions, children, pad = true, description }: { title?: string; description?: string; actions?: React.ReactNode; children: React.ReactNode; pad?: boolean }) {
  return (
    <section className="overflow-hidden rounded-xl border border-line bg-surface shadow-[var(--shadow-card)]">
      {title && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-3">
          <div>
            <h2 className="text-[15px] font-semibold leading-snug">{title}</h2>
            {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
          </div>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
      )}
      <div className={pad ? "p-4" : ""}>{children}</div>
    </section>
  );
}

export function Table({ head, children, empty }: { head: string[]; children: React.ReactNode; empty?: string }) {
  const rows = Array.isArray(children) ? children.filter(Boolean).length : children ? 1 : 0;
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line bg-surface">
            {head.map((h, i) => (
              <th key={h + i} className="whitespace-nowrap px-4 py-2.5 text-xs font-semibold uppercase tracking-[0.06em] text-muted first:pl-4 last:pr-4">
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

export const Tr = ({ children, tone }: { children: React.ReactNode; href?: string; tone?: "danger" | "warn" }) => (
  <tr className={`border-b border-line last:border-0 hover:bg-raised ${tone === "danger" ? "bg-danger-soft/50" : tone === "warn" ? "bg-warn-soft/50" : ""}`}>{children}</tr>
);
export const Td = ({ children, className = "", href }: { children?: React.ReactNode; className?: string; href?: string }) => (
  <td className={`px-4 py-[11px] align-middle first:pl-4 last:pr-4 max-sm:whitespace-nowrap ${className}`}>
    {href ? <Link href={href} className="font-semibold text-accent hover:underline">{children}</Link> : children}
  </td>
);

export function Empty({ text, detail, action }: { text: string; detail?: string; action?: React.ReactNode }) {
  return (
    <div className="px-6 py-10 text-center">
      <div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-accent-soft text-accent">
        <Icon name="inbox" className="h-5 w-5" />
      </div>
      <p className="text-sm font-semibold">{text}</p>
      {detail && <p className="mx-auto mt-1 max-w-md text-sm text-muted">{detail}</p>}
      {action && <div className="mt-4 flex justify-center">{action}</div>}
    </div>
  );
}

export function Loading() {
  return <div className="px-6 py-10 text-center text-sm text-muted">Loading...</div>;
}

const NOTICE = {
  info: "border-line bg-surface text-muted",
  good: "border-[#bfe0c9] bg-good-soft text-good",
  warn: "border-[#f0d9b5] bg-warn-soft text-warn",
  danger: "border-[#f5cdcb] bg-danger-soft text-danger"
};

export function Notice({ tone = "info", title, children }: { tone?: keyof typeof NOTICE; title?: string; children: React.ReactNode }) {
  return (
    <div role={tone === "danger" ? "alert" : "note"} className={`rounded-lg border px-3.5 py-2.5 text-sm leading-relaxed ${NOTICE[tone]}`}>
      {title && <p className="font-semibold">{title}</p>}
      {children}
    </div>
  );
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  const e = error as ApiError;
  const fields = e.fields ? Object.entries(e.fields).map(([k, v]) => `${k}: ${v}`).join("; ") : "";
  return <Notice tone="danger">{e.message || "Something went wrong."} {fields}</Notice>;
}

const tones: Record<string, string> = {
  neutral: "bg-raised text-muted",
  good: "bg-good-soft text-good",
  warn: "bg-warn-soft text-warn",
  danger: "bg-danger-soft text-danger",
  accent: "bg-info-soft text-info"
};

const sentence = (v: string) => {
  const w = v.replaceAll("_", " ").trim().toLowerCase();
  return w.charAt(0).toUpperCase() + w.slice(1);
};

export function Badge({ children, tone = "neutral" }: { children: React.ReactNode; tone?: keyof typeof tones }) {
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-semibold ${tones[tone]}`}>
      {typeof children === "string" ? sentence(children) : children}
    </span>
  );
}

const STATUS_TONE: Record<string, keyof typeof tones> = {
  PAID: "good", COMPLETED: "good", READY: "good", VALIDATED: "good", SIGNED: "good", ACTIVE: "good", AVAILABLE: "good", ACCEPTED: "good", GIVEN: "good", DELIVERED: "good", VERIFIED: "good", DISPENSED: "good", RESOLVED: "good",
  DISCHARGED: "neutral", CLOSED: "neutral", WITHDRAWN: "neutral", DRAFT: "neutral", ROUTINE: "neutral", NORMAL: "neutral",
  PARTIALLY_PAID: "warn", CHECKED_IN: "warn", PENDING: "warn", RESULTED: "warn", PERFORMED: "warn", REPORTED: "warn", SUBMISSION_STUBBED: "warn", TRANSFERRED_OUT: "neutral", LOST_TO_FOLLOW_UP: "danger", STOPPED: "neutral", DIED: "neutral", OCCUPIED: "warn", CLEANING: "warn", PRIORITY: "warn", URGENT: "warn", UNVERIFIED: "warn", IN_PROGRESS: "warn", DUE: "warn",
  ISSUED: "accent", BOOKED: "accent", OPEN: "accent", ADMITTED: "accent", ORDERED: "accent", COLLECTED: "accent", SCHEDULED: "accent",
  NEEDS_ATTENTION: "danger", OVERDUE: "danger", LOST: "danger", FAILED: "danger", VOID: "danger", CANCELLED: "danger", NO_SHOW: "danger", DISABLED: "danger", REVERSED: "danger", OUT_OF_SERVICE: "danger", EMERGENCY: "danger", STAT: "danger", CRITICAL: "danger", SEVERE: "danger"
};

export const Status = ({ value }: { value: string }) => <Badge tone={STATUS_TONE[value] ?? "neutral"}>{value}</Badge>;

const BTN = "inline-flex cursor-pointer items-center justify-center gap-2 whitespace-nowrap min-h-9 rounded-lg px-3.5 py-1.5 text-sm font-semibold disabled:pointer-events-none disabled:opacity-50";
export const buttonCls = {
  primary: `${BTN} bg-accent text-white shadow-sm hover:bg-accent-deep`,
  secondary: `${BTN} border border-line-strong bg-surface text-ink hover:bg-raised`,
  danger: `${BTN} border border-[#f5cdcb] bg-surface text-danger hover:bg-danger-soft`
};

export function Button({ children, onClick, type = "button", variant = "primary", disabled, busy, href }: {
  children: React.ReactNode; onClick?: () => void; type?: "button" | "submit"; variant?: "primary" | "secondary" | "danger"; disabled?: boolean; busy?: boolean; href?: string;
}) {
  const cls = buttonCls[variant];
  if (href) return <Link href={href} className={cls}>{children}</Link>;
  return (
    <button type={type} onClick={onClick} disabled={disabled || busy} className={cls}>
      {busy ? "Working..." : children}
    </button>
  );
}

export function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return (
    <label className="block space-y-1">
      <span className="block text-sm font-semibold text-ink">{label}</span>
      {children}
      {hint && <span className="block text-xs text-muted">{hint}</span>}
    </label>
  );
}

const control = "w-full rounded-lg border border-line-strong bg-surface min-h-9 px-3 py-1.5 text-base text-ink outline-none placeholder:text-faint hover:border-faint focus:border-accent focus:ring-4 focus:ring-accent-soft disabled:bg-raised disabled:opacity-70";
const chevron = "appearance-none bg-[length:16px] bg-[right_0.875rem_center] bg-no-repeat pr-9 bg-[url('data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22none%22%20stroke%3D%22%236b7280%22%20stroke-width%3D%221.75%22%20stroke-linecap%3D%22round%22%20stroke-linejoin%3D%22round%22%3E%3Cpath%20d%3D%22M6%208l4%204%204-4%22%2F%3E%3C%2Fsvg%3E')]";
export const Input = (p: React.InputHTMLAttributes<HTMLInputElement>) => <input {...p} className={`${control} ${p.className ?? ""}`} />;
export const Select = (p: React.SelectHTMLAttributes<HTMLSelectElement>) => <select {...p} className={`${control} ${chevron} cursor-pointer ${p.className ?? ""}`} />;
export const Textarea = (p: React.TextareaHTMLAttributes<HTMLTextAreaElement>) => <textarea rows={3} {...p} className={`${control} ${p.className ?? ""}`} />;

export function Grid({ cols = 3, children }: { cols?: 2 | 3 | 4; children: React.ReactNode }) {
  const c = { 2: "md:grid-cols-2", 3: "md:grid-cols-3", 4: "md:grid-cols-2 xl:grid-cols-4" }[cols];
  return <div className={`grid ${cols === 4 ? "grid-cols-2" : "grid-cols-1"} gap-3 ${c}`}>{children}</div>;
}

const BAR = { good: "bg-accent", warn: "bg-warn", danger: "bg-danger", info: "bg-info" };
const CHIP = { good: "bg-accent-soft text-accent", warn: "bg-warn-soft text-warn", danger: "bg-danger-soft text-danger", info: "bg-info-soft text-info" };

export function Stat({ label, value, tone, hint, icon }: { label: string; value: React.ReactNode; tone?: "warn" | "danger" | "info"; hint?: string; icon?: IconName }) {
  const t = tone ?? "good";
  const short = typeof value === "number" || (typeof value === "string" && value.length <= 6);
  return (
    <div className="relative h-full overflow-hidden rounded-xl border border-line bg-surface px-4 py-3 shadow-[var(--shadow-card)]">
      <span className={`absolute inset-y-0 left-0 w-1 ${BAR[t]}`} aria-hidden="true" />
      <div className="flex items-start justify-between gap-3">
        <p className="text-[11px] font-semibold uppercase tracking-[0.07em] text-muted">{label}</p>
        {icon && <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md ${CHIP[t]}`}><Icon name={icon} className="h-4 w-4" /></span>}
      </div>
      <p className={`mt-1.5 truncate font-semibold leading-none tracking-[-0.01em] tabular-nums ${short ? "text-xl sm:text-[19px]" : "text-[17px] sm:text-[19px]"} ${tone === "danger" ? "text-danger" : tone === "warn" ? "text-warn" : ""}`}>{value}</p>
      {hint && <p className="mt-1.5 text-[12px] leading-snug text-muted">{hint}</p>}
    </div>
  );
}

export function Tabs({ tabs, value, onChange }: { tabs: { key: string; label: string }[]; value: string; onChange: (k: string) => void }) {
  return (
    <div className="flex flex-wrap gap-1">
      {tabs.map((t) => (
        <button key={t.key} type="button" onClick={() => onChange(t.key)}
          className={`cursor-pointer rounded-lg min-h-9 px-3 py-1.5 text-sm font-semibold ${value === t.key ? "bg-accent-soft text-accent-deep" : "text-muted hover:bg-raised hover:text-ink"}`}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

export function More({ onMore, loading }: { onMore: (() => void) | null; loading: boolean }) {
  if (!onMore) return null;
  return <div className="border-t border-line p-3 text-center"><Button variant="secondary" onClick={onMore} busy={loading}>Load more</Button></div>;
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
    <div className="space-y-2 rounded-lg border border-line bg-surface p-3">
      <div className="text-sm">{prompt}</div>
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
      <div className="text-xs font-semibold uppercase tracking-[0.06em] text-muted">{k}</div>
      <div className="mt-0.5 text-sm">{v || <span className="text-faint">-</span>}</div>
    </div>
  );
}

import type { ComponentPropsWithoutRef, ReactNode } from "react";

export function PageHeader({
  title,
  subtitle,
  actions
}: {
  title: string;
  subtitle?: string;
  /** Accepted for older call sites; SmartRE's page header has no eyebrow line. */
  eyebrow?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="mb-3 flex flex-wrap items-start justify-between gap-2">
      <div className="min-w-0">
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">{title}</h1>
        {subtitle ? <p className="mt-0.5 text-xs text-muted">{subtitle}</p> : null}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </header>
  );
}

export function Tabs({
  items,
  active
}: {
  items: { href: string; label: string }[];
  active: string;
}) {
  return (
    <nav className="mb-3 flex flex-wrap items-center gap-1">
      {items.map((item) => (
        <a
          key={item.href}
          href={item.href}
          className={item.href === active ? "sidebar-link-active !py-1" : "sidebar-link !py-1"}
        >
          {item.label}
        </a>
      ))}
    </nav>
  );
}

export function Card({
  children,
  title,
  description,
  actions,
  flush,
  icon
}: {
  children: ReactNode;
  icon?: ReactNode;
  title?: string;
  description?: string;
  actions?: ReactNode;
  /** No inner padding, for a table that should run edge to edge. */
  flush?: boolean;
}) {
  return (
    <section className="card overflow-hidden">
      {title ? (
        <div className="flex flex-wrap items-start justify-between gap-2 border-b border-base px-2.5 py-1.5">
          <div>
            <h2 className="flex items-center gap-2 font-display text-base font-semibold text-gray-900 dark:text-white">
              {icon ? <span className="flex h-3.5 w-3.5 items-center text-muted">{icon}</span> : null}
              {title}
            </h2>
            {description ? <p className="mt-0 text-2xs text-muted">{description}</p> : null}
          </div>
          {actions}
        </div>
      ) : null}
      <div className={flush ? "" : "p-2.5"}>{children}</div>
    </section>
  );
}

/** SmartRE's stat card: 10px uppercase label, 13px semibold tabular value, 10px caption, 24px tinted icon chip. A left edge appears only for warn and danger. */
const STAT_EDGE = {
  good: "",
  accent: "",
  warn: "border-l-2 border-l-amber-400 dark:border-l-amber-500/60",
  danger: "border-l-2 border-l-red-400 dark:border-l-red-500/60"
};
const STAT_ICON = {
  good: "bg-brand-50 text-brand-500 dark:bg-brand-500/10",
  accent: "bg-purple-50 text-purple-500 dark:bg-purple-500/10",
  warn: "bg-amber-50 text-amber-500 dark:bg-amber-500/10",
  danger: "bg-red-50 text-red-500 dark:bg-red-500/10"
};

export function Stat({
  label,
  value,
  hint,
  tone,
  icon
}: {
  label: string;
  value: string;
  hint?: string;
  tone?: "good" | "warn" | "danger" | "accent";
  icon?: ReactNode;
}) {
  const t = tone ?? "good";
  return (
    <div className={`card relative h-full px-[7px] py-[5px] ${STAT_EDGE[t]}`}>
      <p className="truncate pr-5 text-[9px] font-medium uppercase leading-3 tracking-[0.05em] text-muted">{label}</p>
      <p className="truncate text-[10.5px] font-medium leading-[14px] tabular-nums text-gray-900 dark:text-white">{value}</p>
      {hint ? <p className="mt-px truncate text-[8.5px] leading-[11px] text-muted">{hint}</p> : null}
      {icon ? (
        <div className={`absolute right-1.5 top-[5px] flex h-[15px] w-[15px] items-center justify-center rounded ${STAT_ICON[t]} [&>svg]:h-[9px] [&>svg]:w-[9px]`}>{icon}</div>
      ) : null}
    </div>
  );
}

type Variant = "success" | "warning" | "error" | "info" | "muted" | "brand" | "purple";

/** SmartRE's Badge variants. */
const VARIANT: Record<Variant, string> = {
  success: "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400",
  warning: "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400",
  error: "bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400",
  info: "bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400",
  muted: "bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400",
  brand: "bg-brand-50 text-brand-700 dark:bg-brand-500/10 dark:text-brand-400",
  purple: "bg-purple-50 text-purple-600 dark:bg-purple-500/10 dark:text-purple-400"
};

const TONE: Record<string, Variant> = {
  GOOD: "success",
  LIVE: "success",
  STABLE: "success",
  TREATMENT_WINS: "success",
  ACTIVE: "success",
  COMPLETED: "success",
  SETTLED: "success",
  LOW: "success",
  CONFIRMED: "success",
  PAID: "success",
  DELIVERED: "success",
  VERIFIED: "success",
  APPROVED: "success",
  ACCEPTED_VERDICT: "success",
  CANARY: "warning",
  WARN: "warning",
  CONTINUE: "warning",
  OPEN: "warning",
  CLOSED: "muted",
  PENDING: "warning",
  MEDIUM: "warning",
  REVIEWING: "warning",
  ALERT: "error",
  ROLLED_BACK: "error",
  DANGER: "error",
  HIGH: "error",
  CRITICAL: "error",
  SUSPENDED: "error",
  URGENT: "error",
  FAILED: "error",
  REJECTED: "error",
  CANCELLED: "error",
  IN_PROGRESS: "info",
  ASSIGNED: "info",
  ANSWERED: "success",
  ACCEPTED: "info",
  PROCESSING: "info",
  INITIATED: "info",
  SHADOW: "brand",
  CONTROL: "brand",
  CONTROL_WINS: "brand",
  PLANNED: "brand",
  LISTED: "brand",
  TREATMENT: "purple",
  EXPLORED: "purple"
};

function sentence(value: string): string {
  const words = value.replaceAll("_", " ").trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function Badge({ value }: { value: string }) {
  const key = value.toUpperCase().replaceAll(" ", "_");
  return <span className={`badge whitespace-nowrap ${VARIANT[TONE[key] ?? "muted"]}`}>{sentence(value)}</span>;
}

export function EmptyState({
  message,
  detail,
  action
}: {
  message: string;
  detail?: string;
  action?: ReactNode;
}) {
  return (
    <div className="card flex flex-col items-center justify-center px-4 py-6 text-center">
      <h3 className="mb-1 font-display text-base font-semibold">{message}</h3>
      {detail ? <p className="mb-3 max-w-sm text-xs text-muted">{detail}</p> : null}
      {action}
    </div>
  );
}

export function Notice({
  tone = "info",
  children
}: {
  tone?: "info" | "good" | "warn" | "danger";
  children: ReactNode;
}) {
  const styles = {
    info: "bg-gray-50 text-gray-600 dark:bg-white/5 dark:text-gray-300",
    good: "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400",
    warn: "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400",
    danger: "bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400"
  }[tone];
  return <div className={`rounded-lg p-3 text-sm ${styles}`}>{children}</div>;
}

export function Field({
  label,
  hint,
  children
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-xs font-medium text-gray-600 dark:text-gray-400">{label}</span>
      {children}
      {hint ? <span className="text-xs text-gray-400">{hint}</span> : null}
    </label>
  );
}

/* SmartRE's .input-base for every control; a select adds right padding for its arrow. */
export const bareInputClass = "input-base";
export const bareSelectClass = "input-base !pr-8 cursor-pointer";
export const inputClass = "input-base";
export const selectClass = "input-base !pr-8 cursor-pointer";

export const buttonClass = "btn-primary";
export const secondaryButtonClass = "btn-secondary";
export const dangerButtonClass = "btn-danger";

export function Select({
  label,
  hint,
  placeholder = "Choose…",
  options,
  ...props
}: {
  label?: string;
  hint?: string;
  placeholder?: string;
  options: { value: string; label: string }[];
} & Omit<ComponentPropsWithoutRef<"select">, "children">) {
  const field = (
    <select {...props} className={selectClass}>
      <option value="">{placeholder}</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
  return label ? (
    <Field label={label} hint={hint}>
      {field}
    </Field>
  ) : (
    field
  );
}

/** Row borders and hover come from .data-table; kept so call sites compile. */
export const rowClass = "";

export interface HeadCell {
  /** Stable React key; also the field name when the header is a sort link. */
  key: string;
  label: ReactNode;
  numeric?: boolean;
}

export function Table({ head, children }: { head: HeadCell[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="data-table min-w-[640px]">
        <thead>
          <tr>
            {head.map((cell) => (
              <th key={cell.key} className={cell.numeric ? "!text-right" : ""}>
                {cell.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

export function Meter({ value, tone = "accent" }: { value: number; tone?: "accent" | "danger" | "good" }) {
  const percent = Math.max(0, Math.min(1, value)) * 100;
  const colour = tone === "danger" ? "bg-red-500" : "bg-brand-500";
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-white/10">
      <div className={`h-full rounded-full ${colour}`} style={{ width: `${percent}%` }} />
    </div>
  );
}

export function KeyValue({ items }: { items: [string, string][] }) {
  return (
    <dl className="grid gap-x-8 gap-y-3 sm:grid-cols-2">
      {items.map(([key, value]) => (
        <div key={key}>
          <dt className="text-2xs font-semibold uppercase tracking-widest text-gray-400">
            {key}
          </dt>
          <dd className="mt-0.5 break-words text-sm">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

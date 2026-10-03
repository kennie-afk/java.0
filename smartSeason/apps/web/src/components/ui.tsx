import type { ComponentPropsWithoutRef, ReactNode } from "react";

export function PageHeader({
  title,
  subtitle,
  eyebrow,
  actions
}: {
  title: string;
  subtitle?: string;
  eyebrow?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="mb-5 flex flex-wrap items-end justify-between gap-4">
      <div className="max-w-3xl">
        {eyebrow ? (
          <p className="mb-1 text-xs font-semibold uppercase tracking-[0.08em] text-[var(--color-accent)]">
            {eyebrow}
          </p>
        ) : null}
        <h1 className="text-xl font-semibold leading-tight sm:text-2xl">{title}</h1>
        {subtitle ? (
          <p className="mt-1 text-sm leading-relaxed text-[var(--color-muted)]">{subtitle}</p>
        ) : null}
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
    <nav className="mb-5 flex flex-wrap items-center gap-1">
      {items.map((item) => {
        const current = item.href === active;
        return (
          <a
            key={item.href}
            href={item.href}
            className={`rounded-lg px-3 py-1.5 text-sm font-semibold ${
              current
                ? "bg-[var(--color-accent-soft)] text-[var(--color-accent-deep)]"
                : "text-[var(--color-muted)] hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)]"
            }`}
          >
            {item.label}
          </a>
        );
      })}
    </nav>
  );
}

export function Card({
  children,
  title,
  description,
  actions,
  flush
}: {
  children: ReactNode;
  title?: string;
  description?: string;
  actions?: ReactNode;
  /** No inner padding, for a table that should run edge to edge. */
  flush?: boolean;
}) {
  return (
    <section className="overflow-hidden rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] shadow-[var(--shadow-card)]">
      {title ? (
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[var(--color-line)] px-4 py-2.5">
          <div>
            <h2 className="text-[15px] font-semibold leading-snug">{title}</h2>
            {description ? (
              <p className="mt-0.5 text-sm leading-relaxed text-[var(--color-muted)]">{description}</p>
            ) : null}
          </div>
          {actions}
        </div>
      ) : null}
      <div className={flush ? "" : "p-4"}>{children}</div>
    </section>
  );
}

const ACCENT_BAR = {
  good: "bg-[var(--color-accent)]",
  warn: "bg-[var(--color-warn)]",
  danger: "bg-[var(--color-danger)]",
  accent: "bg-[var(--color-violet)]"
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
  const bar = ACCENT_BAR[tone ?? "good"];
  const valueColour = tone === "warn" ? "text-[var(--color-warn)]" : tone === "danger" ? "text-[var(--color-danger)]" : "";
  const numeric = /^[^A-Za-z]*$/.test(value) || /^[\d.,]+\s?(ms|s|%|x|\/s|KB|MB|h|m)$/i.test(value);

  return (
    <div className="relative h-full overflow-hidden rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] px-4 py-3 shadow-[var(--shadow-card)]">
      {tone === "warn" || tone === "danger" ? (
        <span className={`absolute inset-y-0 left-0 w-[3px] ${bar}`} aria-hidden="true" />
      ) : null}
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-semibold uppercase tracking-[0.07em] text-[var(--color-muted)]">{label}</p>
        {icon ? (
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
            {icon}
          </span>
        ) : null}
      </div>
      <p
        className={`mt-2 truncate font-semibold tabular-nums leading-none tracking-[-0.01em] ${
          numeric ? "text-[19px]" : "text-base"
        } ${valueColour}`}
      >
        {value}
      </p>
      {hint ? <p className="mt-1.5 text-[12px] leading-snug text-[var(--color-muted)]">{hint}</p> : null}
    </div>
  );
}

const TONE: Record<string, string> = {
  GOOD: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  LIVE: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  STABLE: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  TREATMENT_WINS: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  CANARY: "bg-[var(--color-warn-soft)] text-[var(--color-warn)]",
  WARN: "bg-[var(--color-warn-soft)] text-[var(--color-warn)]",
  CONTINUE: "bg-[var(--color-warn-soft)] text-[var(--color-warn)]",
  ALERT: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  ROLLED_BACK: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  DANGER: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  SHADOW: "bg-[var(--color-accent-soft)] text-[var(--color-accent)]",
  CONTROL: "bg-[var(--color-accent-soft)] text-[var(--color-accent)]",
  CONTROL_WINS: "bg-[var(--color-accent-soft)] text-[var(--color-accent)]",
  TREATMENT: "bg-[var(--color-violet-soft)] text-[var(--color-violet)]",
  EXPLORED: "bg-[var(--color-violet-soft)] text-[var(--color-violet)]",
  ACTIVE: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  COMPLETED: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  SETTLED: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  LOW: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  OPEN: "bg-[var(--color-warn-soft)] text-[var(--color-warn)]",
  PENDING: "bg-[var(--color-warn-soft)] text-[var(--color-warn)]",
  MEDIUM: "bg-[var(--color-warn-soft)] text-[var(--color-warn)]",
  REVIEWING: "bg-[var(--color-warn-soft)] text-[var(--color-warn)]",
  HIGH: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  CRITICAL: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  SUSPENDED: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  IN_PROGRESS: "bg-[var(--color-info-soft)] text-[var(--color-info)]",
  ASSIGNED: "bg-[var(--color-info-soft)] text-[var(--color-info)]",
  ACCEPTED: "bg-[var(--color-info-soft)] text-[var(--color-info)]",
  PROCESSING: "bg-[var(--color-info-soft)] text-[var(--color-info)]",
  INITIATED: "bg-[var(--color-info-soft)] text-[var(--color-info)]",
  CONFIRMED: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  PAID: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  DELIVERED: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  VERIFIED: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  APPROVED: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  ACCEPTED_VERDICT: "bg-[var(--color-good-soft)] text-[var(--color-good)]",
  URGENT: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  FAILED: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  REJECTED: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  CANCELLED: "bg-[var(--color-danger-soft)] text-[var(--color-danger)]",
  PLANNED: "bg-[var(--color-accent-soft)] text-[var(--color-accent)]",
  LISTED: "bg-[var(--color-accent-soft)] text-[var(--color-accent)]"
};

function sentence(value: string): string {
  const words = value.replaceAll("_", " ").trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function Badge({ value }: { value: string }) {
  const key = value.toUpperCase().replaceAll(" ", "_");
  const tone = TONE[key] ?? "bg-[var(--color-raised)] text-[var(--color-muted)]";
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${tone}`}
    >
      {sentence(value)}
    </span>
  );
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
    <div className="rounded-xl border border-dashed border-[var(--color-line-strong)] bg-[var(--color-surface)] px-6 py-10 text-center">
      <div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-accent-soft)]">
        <svg viewBox="0 0 24 24" className="h-5 w-5 text-[var(--color-accent)]" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 20v-8" />
          <path d="M12 12c-3.5 0-5-2.2-5-5 3.5 0 5 2.2 5 5z" />
          <path d="M12 12c3.5 0 5-2.2 5-5-3.5 0-5 2.2-5 5z" />
          <path d="M5 20h14" />
        </svg>
      </div>
      <p className="text-[15px] font-semibold">{message}</p>
      {detail ? (
        <p className="mx-auto mt-1.5 max-w-md text-sm text-[var(--color-muted)]">{detail}</p>
      ) : null}
      {action ? <div className="mt-5 flex justify-center">{action}</div> : null}
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
    info: "border-[var(--color-line)] bg-[var(--color-surface)] text-[var(--color-muted)]",
    good: "border-[#bfe0c9] bg-[var(--color-good-soft)] text-[var(--color-good)]",
    warn: "border-[#f0d9b5] bg-[var(--color-warn-soft)] text-[var(--color-warn)]",
    danger: "border-[#f5cdcb] bg-[var(--color-danger-soft)] text-[var(--color-danger)]"
  }[tone];
  return (
    <div className={`rounded-lg border px-3.5 py-2.5 text-sm leading-relaxed ${styles}`}>
      {children}
    </div>
  );
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
    <label className="block">
      <span className="block text-sm font-semibold text-[var(--color-ink)]">{label}</span>
      {children}
      {hint ? (
        <span className="mt-1 block text-xs text-[var(--color-muted)]">{hint}</span>
      ) : null}
    </label>
  );
}

/* Margin-free variants. `inputClass`/`selectClass` carry `mt-2` for the `Field`
   wrapper; forms that supply their own label spacing use these instead. */
export const bareInputClass =
  "w-full rounded-lg border border-[var(--color-line-strong)] bg-[var(--color-surface)] min-h-9 px-3 py-1.5 text-base outline-none placeholder:text-[var(--color-faint)] hover:border-[var(--color-faint)] focus:border-[var(--color-accent)]";

export const bareSelectClass =
  "w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-line-strong)] bg-[var(--color-surface)] bg-[length:14px] bg-[right_0.625rem_center] bg-no-repeat min-h-9 py-1.5 pl-3 pr-8 text-sm outline-none hover:border-[var(--color-faint)] focus:border-[var(--color-accent)] disabled:opacity-60 bg-[url('data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22none%22%20stroke%3D%22%236b7280%22%20stroke-width%3D%221.75%22%20stroke-linecap%3D%22round%22%20stroke-linejoin%3D%22round%22%3E%3Cpath%20d%3D%22M6%208l4%204%204-4%22%2F%3E%3C%2Fsvg%3E')]";

export const inputClass =
  "mt-1.5 w-full rounded-lg border border-[var(--color-line-strong)] bg-[var(--color-surface)] min-h-9 px-3 py-1.5 text-base outline-none placeholder:text-[var(--color-faint)] hover:border-[var(--color-faint)] focus:border-[var(--color-accent)] focus:ring-4 focus:ring-[var(--color-accent-soft)]";

export const selectClass =
  "mt-1.5 w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-line-strong)] bg-[var(--color-surface)] bg-[length:16px] bg-[right_0.875rem_center] bg-no-repeat min-h-9 py-1.5 pl-3 pr-9 text-base outline-none hover:border-[var(--color-faint)] focus:border-[var(--color-accent)] focus:ring-4 focus:ring-[var(--color-accent-soft)] bg-[url('data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22none%22%20stroke%3D%22%236b7280%22%20stroke-width%3D%221.75%22%20stroke-linecap%3D%22round%22%20stroke-linejoin%3D%22round%22%3E%3Cpath%20d%3D%22M6%208l4%204%204-4%22%2F%3E%3C%2Fsvg%3E')]";

const buttonBase =
  "inline-flex cursor-pointer items-center justify-center gap-2 rounded-lg min-h-9 px-3.5 py-1.5 text-sm font-semibold disabled:pointer-events-none disabled:opacity-50";

export const buttonClass = `${buttonBase} bg-[var(--color-accent)] text-white shadow-sm hover:bg-[var(--color-good)]`;

export const secondaryButtonClass = `${buttonBase} border border-[var(--color-line-strong)] bg-[var(--color-surface)] text-[var(--color-ink)] hover:bg-[var(--color-raised)]`;

export const dangerButtonClass = `${buttonBase} border border-[#f5cdcb] bg-[var(--color-surface)] text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)]`;

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

export const rowClass =
  "border-b border-[var(--color-line)] transition-colors last:border-0 hover:bg-[var(--color-raised)]";

export interface HeadCell {
  /** Stable React key; also the field name when the header is a sort link. */
  key: string;
  label: ReactNode;
  numeric?: boolean;
}

export function Table({ head, children }: { head: HeadCell[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[680px] text-sm">
        <thead>
          <tr className="border-b border-[var(--color-line)] bg-[var(--color-surface)] text-left">
            {head.map((cell) => (
              <th
                key={cell.key}
                className={`whitespace-nowrap px-4 py-2.5 text-xs font-semibold uppercase tracking-[0.06em] text-[var(--color-muted)] ${
                  cell.numeric ? "text-right" : ""
                }`}
              >
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
  const colour = {
    accent: "var(--color-accent)",
    danger: "var(--color-danger)",
    good: "var(--color-good)"
  }[tone];
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-[var(--color-line)]">
      <div className="h-full rounded-full transition-all" style={{ width: `${percent}%`, background: colour }} />
    </div>
  );
}

export function KeyValue({ items }: { items: [string, string][] }) {
  return (
    <dl className="grid gap-x-8 gap-y-3 sm:grid-cols-2">
      {items.map(([key, value]) => (
        <div key={key}>
          <dt className="text-xs font-semibold uppercase tracking-[0.06em] text-[var(--color-muted)]">
            {key}
          </dt>
          <dd className="mt-0.5 break-words text-sm">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

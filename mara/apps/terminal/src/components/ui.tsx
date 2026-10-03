"use client";

/**
 * The shared UI layer. Every screen builds from these; a page that hand-rolls its own
 * table row, card, empty state or delete confirm bypasses hover, density and radius and
 * should be fixed here instead (see the grep noted in the README).
 */
import Link from "next/link";
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";
import type { Page } from "@/lib/page";

export function PageHeader({ title, sub, actions }: { title: string; sub?: string; actions?: ReactNode }) {
  return (
    <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {sub ? <p className="mt-0.5 text-xs text-muted">{sub}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`card p-3 ${className}`}>{children}</section>;
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return <h2 className="mb-2 text-2xs font-semibold uppercase tracking-wider text-muted">{children}</h2>;
}

type ButtonProps = {
  children: ReactNode;
  variant?: "default" | "primary" | "danger" | "danger-solid";
  className?: string;
};
const variantClass = {
  default: "",
  primary: "btn-primary",
  danger: "btn-danger",
  "danger-solid": "btn-danger-solid"
};

export function Button({
  children,
  variant = "default",
  className = "",
  ...rest
}: ButtonProps & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type="button" className={`btn ${variantClass[variant]} ${className}`} {...rest}>
      {children}
    </button>
  );
}

export function LinkButton({
  href,
  children,
  variant = "default",
  className = ""
}: ButtonProps & { href: string }) {
  return (
    <Link href={href} className={`btn ${variantClass[variant]} ${className}`}>
      {children}
    </Link>
  );
}

export function Field({
  label,
  hint,
  error,
  children
}: {
  label: string;
  hint?: string;
  error?: string | null;
  children: ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium">{label}</span>
      {children}
      {error ? (
        <span role="alert" className="mt-1 block text-xs text-danger">
          {error}
        </span>
      ) : hint ? (
        <span className="mt-1 block text-xs text-muted">{hint}</span>
      ) : null}
    </label>
  );
}

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`input ${props.className ?? ""}`} />;
}

export function Select(props: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`input ${props.className ?? ""}`} />;
}

const tones = {
  info: "border-line bg-raised text-ink",
  good: "border-good/30 bg-good-soft text-good",
  warn: "border-warn/30 bg-warn-soft text-warn",
  danger: "border-danger/30 bg-danger-soft text-danger"
};

export function Notice({
  tone = "info",
  title,
  children
}: {
  tone?: keyof typeof tones;
  title?: string;
  children: ReactNode;
}) {
  return (
    <div role={tone === "danger" ? "alert" : "status"} className={`rounded-md border px-3 py-2 text-xs ${tones[tone]}`}>
      {title ? <div className="mb-0.5 font-semibold">{title}</div> : null}
      <div>{children}</div>
    </div>
  );
}

export function Badge({ tone = "info", children }: { tone?: keyof typeof tones; children: ReactNode }) {
  return (
    <span className={`inline-block rounded-md border px-1.5 py-px text-2xs font-semibold ${tones[tone]}`}>{children}</span>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-1 px-3 py-9 text-center">
      <div className="text-base font-semibold">{title}</div>
      {children ? <div className="max-w-md text-xs text-muted">{children}</div> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}

export function Loading() {
  return <div className="px-3 py-10 text-center text-xs text-muted">Loading from this device...</div>;
}

/** Table shell. Rows use the shared `.tbl` styles, which supply hover and density. */
export function Table({
  head,
  children,
  bare = false
}: {
  head: { label: string; num?: boolean }[];
  children: ReactNode;
  /** inside a Card already: drop the table's own border */
  bare?: boolean;
}) {
  return (
    <div className={bare ? "overflow-x-auto" : "card overflow-x-auto"}>
      <table className="tbl">
        <thead>
          <tr>
            {head.map((h) => (
              <th key={h.label} className={h.num ? "num" : ""}>
                {h.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

export function Pager({
  shown,
  hasPrev,
  hasNext,
  onPrev,
  onNext,
  noun
}: {
  shown: number;
  hasPrev: boolean;
  hasNext: boolean;
  onPrev: () => void;
  onNext: () => void;
  noun: string;
}) {
  return (
    <nav aria-label="Pagination" className="mt-2 flex items-center justify-between text-xs text-muted">
      <span>
        {shown} {noun} on this page
      </span>
      <span className="flex gap-2">
        <Button aria-disabled={!hasPrev} disabled={!hasPrev} onClick={onPrev}>
          Previous
        </Button>
        <Button aria-disabled={!hasNext} disabled={!hasNext} onClick={onNext}>
          Next
        </Button>
      </span>
    </nav>
  );
}

/**
 * Keyset paging over a loader. Holds the stack of cursors already visited so Previous is
 * exact, and reloads from the first page when `deps` (the filter) change. Nothing here
 * ever asks for more than one page.
 */
export function usePagedList<T, C>(load: (after: C | null) => Promise<Page<T, C>>, deps: unknown[]) {
  const [stack, setStack] = useState<(C | null)[]>([null]);
  const [page, setPage] = useState<Page<T, C> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loadRef = useRef(load);
  loadRef.current = load;
  const key = JSON.stringify(deps);

  useEffect(() => {
    setStack([null]);
  }, [key]);

  const current = stack[stack.length - 1];
  const reload = useCallback(() => {
    let cancelled = false;
    loadRef
      .current(current)
      .then((p) => !cancelled && (setPage(p), setError(null)))
      .catch((e: unknown) => !cancelled && setError(e instanceof Error ? e.message : "could not read local data"));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current, key]);

  useEffect(() => reload(), [reload]);

  return {
    page,
    error,
    reload,
    hasPrev: stack.length > 1,
    hasNext: Boolean(page?.next),
    next: () => page?.next && setStack((s) => [...s, page.next]),
    prev: () => setStack((s) => (s.length > 1 ? s.slice(0, -1) : s))
  };
}

/**
 * Inline delete confirmation: the first press swaps the button for an explicit
 * "Delete / Cancel" pair in place. Never window.confirm.
 */
export function ConfirmDelete({
  label,
  what,
  onConfirm
}: {
  label: string;
  what: string;
  onConfirm: () => Promise<void> | void;
}) {
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState(false);
  if (!asking) {
    return (
      <Button variant="danger" onClick={() => setAsking(true)}>
        {label}
      </Button>
    );
  }
  return (
    <span className="inline-flex items-center gap-2 rounded-md border border-danger/30 bg-danger-soft px-2 py-1 text-xs">
      <span>Delete {what}? This cannot be undone.</span>
      <Button
        variant="danger-solid"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          try {
            await onConfirm();
          } finally {
            setBusy(false);
          }
        }}
      >
        Delete
      </Button>
      <Button onClick={() => setAsking(false)}>Cancel</Button>
    </span>
  );
}

export function KeyValue({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-xs">
      {rows.map(([k, v]) => (
        <div key={k} className="contents">
          <dt className="text-muted">{k}</dt>
          <dd className="min-w-0 break-words">{v}</dd>
        </div>
      ))}
    </dl>
  );
}

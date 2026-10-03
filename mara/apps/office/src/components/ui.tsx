"use client";

import { type ReactNode } from "react";

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

export function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="card p-3">
      <div className="text-2xs font-semibold uppercase tracking-wider text-muted">{label}</div>
      <div className="mt-1 text-3xl font-semibold tabular-nums">{value}</div>
      {sub ? <div className="mt-0.5 text-xs text-muted">{sub}</div> : null}
    </div>
  );
}

const variantClass = { default: "", primary: "btn-primary", danger: "btn-danger" };

export function Button({
  children, variant = "default", className = "", ...rest
}: { children: ReactNode; variant?: keyof typeof variantClass; className?: string } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type="button" className={`btn ${variantClass[variant]} ${className}`} {...rest}>
      {children}
    </button>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium">{label}</span>
      {children}
      {hint ? <span className="mt-1 block text-xs text-muted">{hint}</span> : null}
    </label>
  );
}

export const Input = (p: React.InputHTMLAttributes<HTMLInputElement>) => <input {...p} className={`input ${p.className ?? ""}`} />;
export const Select = (p: React.SelectHTMLAttributes<HTMLSelectElement>) => <select {...p} className={`input ${p.className ?? ""}`} />;

const tones = {
  info: "border-line bg-raised text-ink",
  good: "border-good/30 bg-good-soft text-good",
  warn: "border-warn/30 bg-warn-soft text-warn",
  danger: "border-danger/30 bg-danger-soft text-danger"
};

export function Notice({ tone = "info", title, children }: { tone?: keyof typeof tones; title?: string; children: ReactNode }) {
  return (
    <div role={tone === "danger" ? "alert" : "status"} className={`rounded-md border px-3 py-2 text-xs ${tones[tone]}`}>
      {title ? <div className="mb-0.5 font-semibold">{title}</div> : null}
      <div>{children}</div>
    </div>
  );
}

export function Badge({ tone = "info", children }: { tone?: keyof typeof tones; children: ReactNode }) {
  return <span className={`inline-block rounded-md border px-1.5 py-px text-2xs font-semibold ${tones[tone]}`}>{children}</span>;
}

export function statusTone(status: string): keyof typeof tones {
  return status === "ACTIVE" ? "good" : status === "SUSPENDED" ? "warn" : "danger";
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="px-3 py-8 text-center text-xs text-muted">{children}</div>;
}

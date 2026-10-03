import Link from "next/link";

/**
 * Column header that toggles the sort. Clicking the active column reverses it;
 * clicking another sorts by that one ascending.
 */
export function SortLink({
  basePath,
  field,
  label,
  activeSort,
  numeric,
  extra
}: {
  basePath: string;
  field: string;
  label: string;
  activeSort: string | null;
  numeric?: boolean;
  /** Active search and filters, kept when the sort changes. */
  extra?: Record<string, string>;
}) {
  const [activeField, activeDirection] = (activeSort ?? "").split(",");
  const isActive = activeField === field;
  const next = isActive && activeDirection === "asc" ? "desc" : "asc";

  const params = new URLSearchParams({ sort: `${field},${next}`, ...(extra ?? {}) });

  return (
    <Link
      href={`${basePath}?${params.toString()}`}
      className={`inline-flex items-center gap-1 transition-colors hover:text-[var(--color-ink)] ${
        numeric ? "flex-row-reverse" : ""
      } ${isActive ? "text-[var(--color-ink)]" : ""}`}
    >
      {label}
      <span aria-hidden="true" className={isActive ? "" : "opacity-0"}>
        {activeDirection === "desc" ? "↓" : "↑"}
      </span>
    </Link>
  );
}

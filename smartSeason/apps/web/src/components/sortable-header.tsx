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
  numeric
}: {
  basePath: string;
  field: string;
  label: string;
  activeSort: string | null;
  numeric?: boolean;
}) {
  const [activeField, activeDirection] = (activeSort ?? "").split(",");
  const isActive = activeField === field;
  const next = isActive && activeDirection === "asc" ? "desc" : "asc";

  return (
    <Link
      href={`${basePath}?sort=${field},${next}`}
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

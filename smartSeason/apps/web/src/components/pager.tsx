import Link from "next/link";
import { PAGE_SIZE } from "@/lib/load";

/**
 * Page controls for a list. Renders nothing when everything already fits, so a
 * cooperative with three farms never sees paging chrome it does not need.
 */
export function Pager({
  basePath,
  page,
  totalElements,
  totalPages,
  sort
}: {
  basePath: string;
  page: number;
  totalElements: number;
  totalPages: number;
  /** Carried across pages so paging does not silently reset the sort. */
  sort?: string | null;
}) {
  if (totalPages <= 1) {
    return null;
  }

  const first = page * PAGE_SIZE + 1;
  const last = Math.min((page + 1) * PAGE_SIZE, totalElements);
  const href = (target: number) => {
    const params = new URLSearchParams();
    if (target > 0) params.set("page", String(target));
    if (sort) params.set("sort", sort);
    const query = params.toString();
    return query ? `${basePath}?${query}` : basePath;
  };

  const link =
    "rounded-md border border-[var(--color-line)] px-2.5 py-1 text-[var(--color-ink)] hover:border-[var(--color-muted)]";
  const disabled =
    "rounded-md border border-[var(--color-line)] px-2.5 py-1 text-[var(--color-muted)] opacity-50";

  return (
    <div className="mt-3 flex items-center justify-between text-xs text-[var(--color-muted)]">
      <span className="tabular-nums">
        {first}–{last} of {totalElements}
      </span>
      <div className="flex items-center gap-2">
        {page > 0 ? (
          <Link href={href(page - 1)} className={link} rel="prev">
            Previous
          </Link>
        ) : (
          <span className={disabled}>Previous</span>
        )}
        <span className="tabular-nums">
          Page {page + 1} of {totalPages}
        </span>
        {page + 1 < totalPages ? (
          <Link href={href(page + 1)} className={link} rel="next">
            Next
          </Link>
        ) : (
          <span className={disabled}>Next</span>
        )}
      </div>
    </div>
  );
}

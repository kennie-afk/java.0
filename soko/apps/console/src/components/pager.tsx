import Link from "next/link";
import { buttonClass, inputClass, secondaryButtonClass } from "@/components/ui";

export const PAGE_SIZE = 50;

export interface PagingQuery {
  q?: string;
  page?: string;
}

/** Reads ?q= and ?page= (1-based in the URL, 0-based for the API). */
export function parsePaging(params: PagingQuery): { q: string; page: number } {
  const q = (params.q ?? "").trim().slice(0, 80);
  const page = Math.max(1, Number.parseInt(params.page ?? "1", 10) || 1) - 1;
  return { q, page };
}

/** The query string the list endpoints take: search text, 0-based page, fixed page size. */
export function listQuery(q: string, page: number): string {
  const params = new URLSearchParams({ limit: String(PAGE_SIZE), page: String(page) });
  if (q) params.set("q", q);
  return params.toString();
}

/** A plain GET form, so search works without client code and the result is a shareable URL. */
export function SearchBar({ q, placeholder }: { q: string; placeholder: string }) {
  return (
    <form method="get" className="mb-4 flex max-w-md items-center gap-2" role="search">
      <input
        name="q"
        defaultValue={q}
        placeholder={placeholder}
        aria-label={placeholder}
        maxLength={80}
        className={`${inputClass} !mt-0`}
      />
      <button type="submit" className={buttonClass}>Search</button>
      {q ? <Link href="?" className={secondaryButtonClass}>Clear</Link> : null}
    </form>
  );
}

function href(q: string, page: number): string {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (page > 0) params.set("page", String(page + 1));
  const text = params.toString();
  return text ? `?${text}` : "?";
}

/** "51-100 of 123" with previous/next. Says so plainly when the search matched nothing. */
export function Pager({
  q,
  page,
  shown,
  total,
  hasMore
}: {
  q: string;
  page: number;
  shown: number;
  total: number;
  hasMore: boolean;
}) {
  const first = shown === 0 ? 0 : page * PAGE_SIZE + 1;
  const last = page * PAGE_SIZE + shown;
  return (
    <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-[0.958rem] text-[var(--color-muted)]">
      <span className="tabular-nums">
        {total === 0
          ? q ? `Nothing matches "${q}".` : "Nothing here yet."
          : `${first}-${last} of ${total}`}
      </span>
      <span className="flex gap-2">
        {page > 0 ? <Link href={href(q, page - 1)} className={secondaryButtonClass}>Previous</Link> : null}
        {hasMore ? <Link href={href(q, page + 1)} className={secondaryButtonClass}>Next</Link> : null}
      </span>
    </div>
  );
}

'use client'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Page controls for a server-paged list.
 *
 * <p>Always states the range and the total — "1–20 of 412" — because the number that
 * matters to someone with a large portfolio is the one they cannot see. A list that shows
 * twenty rows without saying how many exist is the failure this component was written to
 * end: the previous portfolio page fetched a capped hundred and presented it as
 * everything.
 *
 * <p>Renders nothing for a single page. A pagination bar under six rows is furniture that
 * earns nothing.
 */
export default function PagedNav({ page, size, total, onPage, busy }: {
  /** Zero-based, matching Spring's Pageable. */
  page: number
  size: number
  total: number
  onPage: (page: number) => void
  busy?: boolean
}) {
  const pages = Math.max(1, Math.ceil(total / size))
  if (pages <= 1) return null

  const first = page * size + 1
  const last = Math.min((page + 1) * size, total)
  const atStart = page === 0
  const atEnd = page >= pages - 1

  const step = (to: number) => (
    <button
      type="button"
      onClick={() => onPage(to)}
      disabled={busy || (to < page ? atStart : atEnd)}
      aria-label={to < page ? 'Previous page' : 'Next page'}
      className={cn(
        'w-6 h-6 rounded-md border border-base flex items-center justify-center',
        'text-gray-600 dark:text-gray-400 transition-colors',
        'hover:border-gold-300 dark:hover:border-gold-500/40 hover:text-gray-900 dark:hover:text-white',
        'disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:border-[color:var(--border)]')}>
      {to < page ? <ChevronLeft size={13}/> : <ChevronRight size={13}/>}
    </button>
  )

  return (
    <nav
      aria-label="Pagination"
      className="flex items-center justify-between gap-3 px-3 py-2 border-t border-base">
      {/* aria-live so a screen reader hears the range change rather than only the rows
          silently swapping underneath. */}
      <p className="text-2xs text-muted tabular-nums" aria-live="polite">
        {first.toLocaleString()}–{last.toLocaleString()} of {total.toLocaleString()}
      </p>
      <div className="flex items-center gap-1.5">
        {step(page - 1)}
        <span className="text-2xs text-muted tabular-nums px-1">
          {page + 1} / {pages}
        </span>
        {step(page + 1)}
      </div>
    </nav>
  )
}

/**
 * The original control, kept because two admin screens use it and it is the right shape
 * where the caller knows the page count but not the row total.
 *
 * <p>Prefer {@link PagedNav} for anything new: it states the range and the total, and the
 * total is the number a landlord with a large portfolio actually needs.
 */
export function Pagination({ page, totalPages, onPageChange, className }: {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  className?: string
}) {
  if (totalPages <= 1) return null

  return (
    <nav aria-label="Pagination"
         className={`flex items-center justify-between gap-3 pt-1 ${className || ''}`}>
      <p className="text-2xs text-muted tabular-nums" aria-live="polite">
        Page {page + 1} of {totalPages}
      </p>
      <div className="flex items-center gap-1.5">
        <button
          type="button" aria-label="Previous page"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
          className="w-6 h-6 rounded-md border border-base flex items-center justify-center text-gray-600 dark:text-gray-400 transition-colors hover:border-gold-300 dark:hover:border-gold-500/40 disabled:opacity-40 disabled:cursor-not-allowed">
          <ChevronLeft size={13}/>
        </button>
        <button
          type="button" aria-label="Next page"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          className="w-6 h-6 rounded-md border border-base flex items-center justify-center text-gray-600 dark:text-gray-400 transition-colors hover:border-gold-300 dark:hover:border-gold-500/40 disabled:opacity-40 disabled:cursor-not-allowed">
          <ChevronRight size={13}/>
        </button>
      </div>
    </nav>
  )
}

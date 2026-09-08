'use client'
import { cn } from '@/lib/utils'
import { ReactNode, HTMLAttributes } from 'react'

interface CardProps extends HTMLAttributes<HTMLDivElement> { hover?: boolean; padding?: 'none'|'sm'|'md'|'lg' }
// Tightened one step each. A card is a boundary around content, and the content is what
// the reader came for — generous padding on a dense dashboard just pushes the next card
// below the fold.
const pads = { none:'p-0', sm:'p-2', md:'p-3', lg:'p-4' }

export function Card({ hover, padding='md', className, children, ...p }: CardProps) {
  return <div className={cn('card', pads[padding], hover && 'card-hover cursor-pointer', className)} {...p}>{children}</div>
}

export function StatCard({ label, value, sub, icon, color='gold' }:
  { label:string; value:ReactNode; sub?:ReactNode; icon?:ReactNode; color?:'gold'|'emerald'|'blue'|'purple' }) {
  const accent: Record<string,string> = {
    gold:'border-l-gold-400 dark:border-l-gold-500/60',
    emerald:'border-l-emerald-400 dark:border-l-emerald-500/60',
    blue:'border-l-blue-400 dark:border-l-blue-500/60',
    purple:'border-l-purple-400 dark:border-l-purple-500/60',
  }
  const iconColor: Record<string,string> = {
    gold:'text-gold-500', emerald:'text-emerald-500', blue:'text-blue-500', purple:'text-purple-500',
  }
  return (
    <Card className={cn('group relative border-l-2', accent[color])}>
      <div className="flex items-start justify-between gap-2.5">
        <div className="min-w-0">
          <p className="text-2xs font-medium text-muted mb-1 truncate uppercase tracking-[0.06em]">{label}</p>
          {/* Body size, distinguished by weight rather than scale. A dashboard is read
              by scanning, and scanning wants even texture — a figure several steps
              larger than everything around it interrupts the scan rather than serving
              it. Weight and tabular numerals carry the emphasis instead. */}
          <p className="text-base font-semibold text-gray-900 dark:text-white leading-tight tracking-[-0.01em] tabular-nums truncate">{value}</p>
          {sub && <p className="text-2xs text-muted mt-1.5 truncate">{sub}</p>}
        </div>
        {/* No hover scale. The icon is a label, not a control, and growing it on hover
            drew the eye to the least informative thing in the card. */}
        {icon && (
          <div className={cn('w-6 h-6 rounded-md flex items-center justify-center flex-shrink-0 bg-gray-50 dark:bg-white/5', iconColor[color])}>
            {icon}
          </div>
        )}
      </div>
    </Card>
  )
}

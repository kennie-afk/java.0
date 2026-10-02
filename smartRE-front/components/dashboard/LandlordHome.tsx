'use client'
import Link from 'next/link'
import { useQueries } from '@tanstack/react-query'
import { AlertTriangle, ArrowRight, Building2, Receipt, Users, Wrench } from 'lucide-react'
import { pmsApi } from '@/lib/api'
import { StatCard, Card } from '@/components/ui/Card'
import { StatusBadge } from '@/components/ui/Badge'
import { SkeletonCard } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import RevealCard from '@/components/ui/RevealCard'
import { fmt } from '@/lib/utils'

/**
 * What a landlord opens the app for: is the rent coming in, who is behind, what is broken.
 * The generic dashboard showed the sale marketplace to landlords, which is the one thing
 * they are not here for. Everything below comes from server-side summaries and a filtered,
 * paged query, so it costs the same for 5 units as for 500.
 */
export default function LandlordHome({ firstName }: { firstName: string }) {
  const [summaryQ, overdueQ, repairsQ] = useQueries({
    queries: [
      { queryKey: ['landlord-home', 'summary'], queryFn: () => pmsApi.units.summary() },
      { queryKey: ['landlord-home', 'overdue'], queryFn: () => pmsApi.invoices.mine({ status: 'OVERDUE', page: 0, size: 6 }) },
      { queryKey: ['landlord-home', 'repairs'], queryFn: () => pmsApi.maintenance.mine({ status: 'OPEN', page: 0, size: 5 }) },
    ],
  })
  const s = summaryQ.data
  const overdue = overdueQ.data?.content ?? []
  const repairs = repairsQ.data?.content ?? []
  const today = new Date().toLocaleDateString('en-KE', { weekday: 'long', month: 'long', day: 'numeric' })

  return (
    <div className="space-y-4">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Welcome, {firstName}</h1>
        <p className="text-muted text-sm mt-1">{today}</p>
      </div>

      {(summaryQ.isError || overdueQ.isError) && <InlineError message="Some portfolio data failed to load." />}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {summaryQ.isLoading || !s ? Array(4).fill(0).map((_, i) => <SkeletonCard key={i} />) : [
          <StatCard key="units" label="Units" value={s.totalUnits} sub={`${s.vacantUnits} vacant · ${s.tenants} tenants`} icon={<Building2 size={16} />} color="gold" />,
          <StatCard key="occ" label="Occupancy" value={`${s.occupancyRate.toFixed(1)}%`} sub={`${s.occupiedUnits} occupied`} icon={<Users size={16} />} color="emerald" />,
          <StatCard key="roll" label="Monthly rent roll" value={fmt.currency(s.monthlyRentRoll)} sub={`${s.activeLeases} active leases`} icon={<Receipt size={16} />} color="blue" />,
          <StatCard key="out" label="Outstanding rent" value={fmt.currency(s.outstandingRent)} sub={`${s.overdueInvoices} overdue · ${s.openMaintenance} open repairs`} icon={<AlertTriangle size={16} />} color="purple" />,
        ].map((card, i) => <RevealCard key={card.key} index={i}>{card}</RevealCard>)}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-base text-gray-900 dark:text-white flex items-center gap-2">
              <AlertTriangle size={14} className="text-muted" />Overdue rent
            </h2>
            <Link href="/portfolio?tab=rent" className="text-xs text-gold-600 hover:underline flex items-center gap-1">All rent <ArrowRight size={12} /></Link>
          </div>
          {overdueQ.isLoading ? <div className="skeleton h-[140px] rounded-md" /> : overdue.length === 0 ? (
            <p className="text-sm text-muted py-8 text-center">Nobody is behind. Nice.</p>
          ) : (
            <ul className="divide-y divide-gray-100 dark:divide-white/5">
              {overdue.map(i => (
                <li key={i.id} className="py-2 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 dark:text-white truncate">{i.tenantName ?? 'Tenant'} · {i.unitLabel ?? 'Unit'}</p>
                    <p className="text-2xs text-muted">Due {fmt.date(i.dueDate)}</p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-sm font-semibold tabular-nums">{fmt.currency(i.balance)}</p>
                    <StatusBadge status={i.status} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-base text-gray-900 dark:text-white flex items-center gap-2">
              <Wrench size={14} className="text-muted" />Open repairs
            </h2>
            <Link href="/portfolio?tab=maintenance" className="text-xs text-gold-600 hover:underline flex items-center gap-1">All repairs <ArrowRight size={12} /></Link>
          </div>
          {repairsQ.isLoading ? <div className="skeleton h-[140px] rounded-md" /> : repairs.length === 0 ? (
            <p className="text-sm text-muted py-8 text-center">No open repairs.</p>
          ) : (
            <ul className="divide-y divide-gray-100 dark:divide-white/5">
              {repairs.map(m => (
                <li key={m.id} className="py-2 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 dark:text-white truncate">{m.title}</p>
                    <p className="text-2xs text-muted">{m.unitLabel ?? 'Unit'} · {m.category}</p>
                  </div>
                  <StatusBadge status={m.priority} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}

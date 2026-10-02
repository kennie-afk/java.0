'use client'
import Link from 'next/link'
import { useQueries } from '@tanstack/react-query'
import { ArrowRight, CalendarClock, Home, Receipt, Wrench } from 'lucide-react'
import { pmsApi } from '@/lib/api'
import { StatCard, Card } from '@/components/ui/Card'
import { StatusBadge } from '@/components/ui/Badge'
import { SkeletonCard } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import Button from '@/components/ui/Button'
import { fmt } from '@/lib/utils'

/** A tenant's home: the tenancy, what is due, and what they have asked to have fixed. */
export default function TenantHome({ firstName }: { firstName: string }) {
  const [leaseQ, invoicesQ, repairsQ] = useQueries({
    queries: [
      { queryKey: ['tenant-home', 'lease'], queryFn: () => pmsApi.leases.myTenancy({ page: 0, size: 1 }) },
      { queryKey: ['tenant-home', 'invoices'], queryFn: () => pmsApi.invoices.myTenancy({ page: 0, size: 6 }) },
      { queryKey: ['tenant-home', 'repairs'], queryFn: () => pmsApi.maintenance.myTenancy({ page: 0, size: 4 }) },
    ],
  })
  const lease = leaseQ.data?.content?.[0]
  const invoices = invoicesQ.data?.content ?? []
  const repairs = repairsQ.data?.content ?? []
  const open = invoices.filter(i => i.status !== 'PAID' && i.status !== 'WRITTEN_OFF')
  const due = open.reduce((n, i) => n + i.balance, 0)
  const next = open[0]
  const loading = leaseQ.isLoading || invoicesQ.isLoading

  return (
    <div className="space-y-4">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Welcome, {firstName}</h1>
        <p className="text-muted text-sm mt-1">Your tenancy at a glance</p>
      </div>
      {(leaseQ.isError || invoicesQ.isError) && <InlineError message="Some tenancy data failed to load." />}

      {!loading && !lease ? (
        <Card><p className="text-sm text-muted py-6 text-center">No tenancy is linked to this account yet. Ask your landlord to connect your tenant record.</p></Card>
      ) : (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {loading || !lease ? Array(4).fill(0).map((_, i) => <SkeletonCard key={i} />) : [
            <StatCard key="home" label="Your home" value={lease.unitLabel ?? 'Unit'} sub={`${lease.status.toLowerCase()} since ${fmt.date(lease.startDate)}`} icon={<Home size={16} />} color="gold" />,
            <StatCard key="rent" label="Monthly rent" value={fmt.currency(lease.rentAmount)} sub={`billed on day ${lease.billingDay}`} icon={<Receipt size={16} />} color="emerald" />,
            <StatCard key="due" label="Balance due" value={fmt.currency(due)} sub={open.length ? `${open.length} open invoice${open.length > 1 ? 's' : ''}` : 'all paid up'} icon={<CalendarClock size={16} />} color="blue" />,
            <StatCard key="dep" label="Deposit held" value={fmt.currency(lease.depositHeld)} sub={`${lease.noticePeriodDays}-day notice`} icon={<Home size={16} />} color="purple" />,
          ]}
        </div>
      )}

      {next && (
        <Card className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-2xs uppercase tracking-[0.06em] text-muted">Next to pay</p>
            <p className="text-sm font-semibold tabular-nums">{fmt.currency(next.balance)} <span className="text-muted font-normal">· {next.invoiceNumber}</span></p>
            <p className="text-2xs text-muted">Due {fmt.date(next.dueDate)} <StatusBadge status={next.status} /></p>
          </div>
          <Link href="/my-tenancy"><Button size="sm">Pay with M-Pesa <ArrowRight size={13} /></Button></Link>
        </Card>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-base text-gray-900 dark:text-white flex items-center gap-2"><Receipt size={14} className="text-muted" />Recent invoices</h2>
            <Link href="/my-tenancy" className="text-xs text-gold-600 hover:underline flex items-center gap-1">All <ArrowRight size={12} /></Link>
          </div>
          {invoices.length === 0 ? <p className="text-sm text-muted py-6 text-center">No invoices yet.</p> : (
            <ul className="divide-y divide-gray-100 dark:divide-white/5">
              {invoices.map(i => (
                <li key={i.id} className="py-2 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 dark:text-white truncate">{i.invoiceNumber}</p>
                    <p className="text-2xs text-muted">{fmt.date(i.periodStart)} – {fmt.date(i.periodEnd)}</p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-sm font-semibold tabular-nums">{fmt.currency(i.amountDue)}</p>
                    <StatusBadge status={i.status} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-base text-gray-900 dark:text-white flex items-center gap-2"><Wrench size={14} className="text-muted" />Your repair requests</h2>
            <Link href="/my-tenancy" className="text-xs text-gold-600 hover:underline flex items-center gap-1">Raise one <ArrowRight size={12} /></Link>
          </div>
          {repairs.length === 0 ? <p className="text-sm text-muted py-6 text-center">Nothing reported.</p> : (
            <ul className="divide-y divide-gray-100 dark:divide-white/5">
              {repairs.map(m => (
                <li key={m.id} className="py-2 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 dark:text-white truncate">{m.title}</p>
                    <p className="text-2xs text-muted">{m.category} · raised {fmt.ago(m.createdAt)}</p>
                  </div>
                  <StatusBadge status={m.status} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}

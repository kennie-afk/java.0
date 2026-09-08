'use client'
import { useCallback, useEffect, useState } from 'react'
import { Home, Receipt, Smartphone, Wrench, Plus } from 'lucide-react'
import { pmsApi } from '@/lib/api'
import type { InvoiceResponse, LeaseResponse, MaintenanceResponse } from '@/types'
import { Card, StatCard } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Modal, EmptyState, PageLoader } from '@/components/ui/Modal'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import Textarea from '@/components/ui/Textarea'
import FileUpload from '@/components/ui/FileUpload'
import MaintenancePhotos from '@/components/property/MaintenancePhotos'
import RentReceipt from '@/components/property/RentReceipt'
import RentInvoiceDocument from '@/components/property/RentInvoiceDocument'
import { cn, fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

const INVOICE_STATUS_VARIANT: Record<string,'success'|'warning'|'error'|'info'|'muted'> = {
  PAID:'success', PENDING:'info', PARTIAL:'warning', OVERDUE:'error', WRITTEN_OFF:'muted',
}
const readable = (s:string) => s.replace(/_/g,' ').toLowerCase().replace(/^./, c => c.toUpperCase())

const MAINT_STATUS_VARIANT: Record<string,'success'|'warning'|'error'|'info'|'muted'> = {
  OPEN:'error', ACKNOWLEDGED:'warning', IN_PROGRESS:'info', RESOLVED:'success', CLOSED:'muted', REJECTED:'muted',
}
const CATEGORIES = ['PLUMBING','ELECTRICAL','APPLIANCE','STRUCTURAL','PEST','SECURITY','OTHER']

export default function MyTenancyPage() {
  const [leases, setLeases]     = useState<LeaseResponse[]>([])
  const [invoices, setInvoices] = useState<InvoiceResponse[]>([])
  const [jobs, setJobs]         = useState<MaintenanceResponse[]>([])
  const [loading, setLoading]   = useState(true)
  const [paying, setPaying]     = useState<string|null>(null)
  const [reportOpen, setReportOpen] = useState(false)
  const [busy, setBusy]         = useState(false)
  const [report, setReport]     = useState({ category:'PLUMBING', priority:'MEDIUM', title:'', description:'', imageUrls:[] as string[] })
  const [receiptFor, setReceiptFor] = useState<InvoiceResponse | null>(null)
  const [invoiceFor, setInvoiceFor] = useState<InvoiceResponse | null>(null)

  const reload = useCallback(async () => {
    const [l, i, m] = await Promise.allSettled([
      pmsApi.leases.myTenancy({ size: 50 }),
      pmsApi.invoices.myTenancy({ size: 50 }),
      pmsApi.maintenance.myTenancy({ size: 50 }),
    ])
    if (l.status === 'fulfilled') setLeases(l.value.content || [])
    if (i.status === 'fulfilled') setInvoices(i.value.content || [])
    if (m.status === 'fulfilled') setJobs(m.value.content || [])
    setLoading(false)
  }, [])

  useEffect(() => { reload() }, [reload])

  const pay = async (inv: InvoiceResponse) => {
    setPaying(inv.id)
    try {
      await pmsApi.invoices.pay(inv.id)
      toast.success('Check your phone for the M-Pesa prompt')
      setTimeout(reload, 4000)
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not start the M-Pesa prompt.')
    } finally { setPaying(null) }
  }

  const submitReport = async () => {
    if (!report.title || !report.description) { toast.error('Add a short title and describe the problem.'); return }
    setBusy(true)
    try {
      await pmsApi.maintenance.raiseAsTenant(report)
      toast.success('Reported. Your landlord has been told.')
      setReportOpen(false)
      setReport({ category:'PLUMBING', priority:'MEDIUM', title:'', description:'', imageUrls:[] })
      reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not send that report.')
    } finally { setBusy(false) }
  }

  if (loading) return <PageLoader/>

  const activeLease = leases.find(l => l.status === 'ACTIVE')
  const outstanding = invoices.reduce((sum, i) => sum + (i.balance || 0), 0)
  const nextDue = invoices.filter(i => i.balance > 0).sort((a,b) => a.dueDate.localeCompare(b.dueDate))[0]

  if (leases.length === 0) {
    return (
      <Card padding="none">
        <EmptyState icon={<Home size={24}/>} title="No tenancy on this account"
          desc="If you rent through SmartRE, ask your landlord to link your tenant record to this account. Your rent invoices will then appear here."/>
      </Card>
    )
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">
        <StatCard label="Your home" value={activeLease?.unitLabel || '—'}
          sub={activeLease ? `${fmt.currency(activeLease.rentAmount)} / month` : 'No active lease'}
          icon={<Home size={16}/>} color="gold"/>
        <StatCard label="Outstanding" value={fmt.currency(outstanding)}
          sub={outstanding > 0 ? 'Across all invoices' : 'You are all paid up'}
          icon={<Receipt size={16}/>} color={outstanding > 0 ? 'purple' : 'emerald'}/>
        <StatCard label="Next due" value={nextDue ? fmt.date(nextDue.dueDate) : '—'}
          sub={nextDue ? `${fmt.currency(nextDue.balance)} on ${nextDue.unitLabel}` : 'Nothing outstanding'}
          icon={<Smartphone size={16}/>} color="blue"/>
      </div>

      {activeLease && (
        <Card>
          <p className="text-xs font-medium text-muted uppercase tracking-wide mb-2">Your lease</p>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3 text-base">
            <div><p className="text-muted text-xs">Unit</p><p className="font-medium">{activeLease.unitLabel}</p></div>
            <div><p className="text-muted text-xs">Rent</p><p className="font-medium tabular-nums">{fmt.currency(activeLease.rentAmount)}</p></div>
            <div><p className="text-muted text-xs">Rent falls due</p><p className="font-medium">Day {activeLease.billingDay} of each month</p></div>
            <div><p className="text-muted text-xs">Notice period</p><p className="font-medium">{activeLease.noticePeriodDays} days</p></div>
          </div>
        </Card>
      )}

      <div>
        <h3 className="font-display text-lg font-semibold text-gray-900 dark:text-white mb-3">Rent invoices</h3>
        {invoices.length === 0 ? (
          <Card padding="none">
            <EmptyState icon={<Receipt size={24}/>} title="No invoices yet"
              desc="Your first rent invoice will appear here a few days before it falls due."/>
          </Card>
        ) : (
          <Card padding="none">
            <ul className="divide-y divide-[color:var(--border)]">
              {invoices.map(inv => (
                <li key={inv.id} className="px-3 py-2.5 flex items-start gap-3 flex-wrap">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="text-base font-medium text-gray-900 dark:text-white">
                        {fmt.date(inv.periodStart)} — {fmt.date(inv.periodEnd)}
                      </p>
                      <Badge variant={INVOICE_STATUS_VARIANT[inv.status] ?? 'muted'} size="sm">{readable(inv.status)}</Badge>
                    </div>
                    <p className="text-xs text-muted mt-0.5 tabular-nums">
                      {inv.invoiceNumber} · due {fmt.date(inv.dueDate)}
                    </p>
                    <p className={cn('text-sm mt-1 tabular-nums',
                      inv.balance > 0 ? 'text-gray-900 dark:text-white' : 'text-muted')}>
                      {inv.balance > 0
                        ? <>{fmt.currency(inv.balance)} outstanding <span className="text-muted">of {fmt.currency(inv.amountDue)}</span></>
                        : <>Paid in full — {fmt.currency(inv.amountDue)}</>}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    {/* The invoice is available whatever its state — an unpaid one is
                        exactly the copy a tenant is asked to produce. */}
                    <Button size="sm" variant="ghost" onClick={() => setInvoiceFor(inv)}>
                      Invoice
                    </Button>
                    {inv.amountPaid > 0 && (
                      <Button size="sm" variant="ghost" leftIcon={<Receipt size={14}/>}
                        onClick={() => setReceiptFor(inv)}>
                        Receipt
                      </Button>
                    )}
                    {inv.balance > 0 && inv.status !== 'WRITTEN_OFF' && (
                      <Button size="sm" leftIcon={<Smartphone size={14}/>} loading={paying === inv.id}
                        onClick={() => pay(inv)}>
                        Pay {fmt.currency(inv.balance)}
                      </Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        )}
      </div>

      <div>
        <div className="flex items-center justify-between gap-3 mb-3">
          <h3 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Repairs</h3>
          {activeLease && (
            <Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setReportOpen(true)}>Report a problem</Button>
          )}
        </div>
        {jobs.length === 0 ? (
          <Card padding="none">
            <EmptyState icon={<Wrench size={24}/>} title="Nothing reported"
              desc="If something breaks, report it here with a photo and your landlord is told straight away."
              action={activeLease ? <Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setReportOpen(true)}>Report a problem</Button> : undefined}/>
          </Card>
        ) : (
          <div className="space-y-3">
            {jobs.map(job => (
              <Card key={job.id}>
                <div className="flex items-center gap-2 flex-wrap">
                  <p className="text-lg font-semibold text-gray-900 dark:text-white">{job.title}</p>
                  <Badge variant={MAINT_STATUS_VARIANT[job.status] ?? 'muted'} size="sm">{readable(job.status)}</Badge>
                </div>
                <p className="text-xs text-muted mt-0.5">
                  {job.reference} · {job.category.toLowerCase()} · reported {fmt.ago(job.createdAt)}
                </p>
                <p className="text-base text-gray-700 dark:text-gray-300 mt-2 whitespace-pre-line">{job.description}</p>
                <MaintenancePhotos requestId={job.id} count={job.imageUrls.length} className="mt-2.5"/>
                {job.resolutionNotes && (
                  <p className="text-sm text-emerald-700 dark:text-emerald-400 mt-2">
                    From your landlord: {job.resolutionNotes}
                  </p>
                )}
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* The tenant's own copy. Nothing here names the landlord: the lease this page
          holds carries a landlordId and no name, and printing "Landlord" on a receipt
          says less than leaving the row off. The figures come from the server. */}
      <RentReceipt invoice={receiptFor} onClose={() => setReceiptFor(null)}/>

      <RentInvoiceDocument invoice={invoiceFor} onClose={() => setInvoiceFor(null)}/>

      <Modal open={reportOpen} onClose={() => setReportOpen(false)} title="Report a problem"
        footer={<><Button variant="ghost" onClick={() => setReportOpen(false)}>Cancel</Button>
                  <Button loading={busy} onClick={submitReport}>Send report</Button></>}>
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <Select label="What kind of problem" value={report.category}
              onChange={e => setReport(f => ({...f, category:e.target.value}))}
              options={CATEGORIES.map(c => ({ value:c, label: readable(c) }))}/>
            <Select label="How urgent" value={report.priority}
              onChange={e => setReport(f => ({...f, priority:e.target.value}))}
              options={[{value:'LOW',label:'Low'},{value:'MEDIUM',label:'Medium'},{value:'HIGH',label:'High'},{value:'URGENT',label:'Urgent'}]}/>
          </div>
          <Input label="Short title" placeholder="Kitchen tap is leaking" value={report.title}
            onChange={e => setReport(f => ({...f, title:e.target.value}))}/>
          <Textarea label="What is happening" rows={3} placeholder="Water pools under the sink overnight."
            value={report.description} onChange={e => setReport(f => ({...f, description:e.target.value}))}/>
          <div>
            <p className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">Photos (optional)</p>
            <FileUpload category="MAINTENANCE_PHOTO" accept=".jpg,.jpeg,.png" compact
              label="Add a photo of the problem"
              onUploaded={url => setReport(f => ({...f, imageUrls:[...f.imageUrls, url].slice(0,6)}))}/>
            {report.imageUrls.length > 0 && (
              <p className="text-xs text-muted mt-1.5">{report.imageUrls.length} photo(s) attached</p>
            )}
          </div>
        </div>
      </Modal>
    </div>
  )
}

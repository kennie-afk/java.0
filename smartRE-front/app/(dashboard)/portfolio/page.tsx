'use client'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { Building2, Users, FileText, Plus, DoorOpen, KeyRound, Receipt, AlertTriangle, Wrench } from 'lucide-react'
import { pmsApi, propertyApi } from '@/lib/api'
import { useAuthGuard } from '@/hooks/useAuthGuard'
import { LANDLORD_ROLES } from '@/lib/roles'
import type { UnitResponse, TenantRecord, LeaseResponse, PortfolioSummaryResponse, PropertyResponse, InvoiceResponse, MaintenanceResponse } from '@/types'
import { Card, StatCard } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Modal, EmptyState, PageLoader } from '@/components/ui/Modal'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import Textarea from '@/components/ui/Textarea'
import MaintenancePhotos from '@/components/property/MaintenancePhotos'
import { cn, fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

type Tab = 'units' | 'tenants' | 'leases' | 'rent' | 'maintenance'

const UNIT_STATUS_VARIANT: Record<string,'success'|'warning'|'info'|'muted'> = {
  OCCUPIED:'success', VACANT:'info', UNDER_MAINTENANCE:'warning', RESERVED:'muted',
}
const LEASE_STATUS_VARIANT: Record<string,'success'|'warning'|'error'|'info'|'muted'> = {
  ACTIVE:'success', DRAFT:'info', ENDED:'muted', TERMINATED:'error', RENEWED:'warning',
}
const MAINT_STATUS_VARIANT: Record<string,'success'|'warning'|'error'|'info'|'muted'> = {
  OPEN:'error', ACKNOWLEDGED:'warning', IN_PROGRESS:'info', RESOLVED:'success', CLOSED:'muted', REJECTED:'muted',
}
const MAINT_PRIORITY_VARIANT: Record<string,'success'|'warning'|'error'|'muted'> = {
  URGENT:'error', HIGH:'warning', MEDIUM:'muted', LOW:'muted',
}
const NEXT_STATUS: Record<string, { value:string; label:string }[]> = {
  OPEN:         [{value:'ACKNOWLEDGED',label:'Acknowledge'},{value:'IN_PROGRESS',label:'Start work'},{value:'REJECTED',label:'Reject'}],
  ACKNOWLEDGED: [{value:'IN_PROGRESS',label:'Start work'},{value:'RESOLVED',label:'Mark resolved'},{value:'REJECTED',label:'Reject'}],
  IN_PROGRESS:  [{value:'RESOLVED',label:'Mark resolved'},{value:'REJECTED',label:'Reject'}],
  RESOLVED:     [{value:'CLOSED',label:'Close'},{value:'IN_PROGRESS',label:'Reopen'}],
  CLOSED:       [],
  REJECTED:     [],
}
const INVOICE_STATUS_VARIANT: Record<string,'success'|'warning'|'error'|'info'|'muted'> = {
  PAID:'success', PENDING:'info', PARTIAL:'warning', OVERDUE:'error', WRITTEN_OFF:'muted',
}
const readable = (s:string) => s.replace(/_/g,' ').toLowerCase().replace(/^./, c => c.toUpperCase())

export default function PortfolioPage() {
  const { ready } = useAuthGuard(LANDLORD_ROLES)
  const params = useSearchParams()
  const router = useRouter()
  const tab = (params.get('tab') as Tab) || 'units'

  const [summary, setSummary]   = useState<PortfolioSummaryResponse|null>(null)
  const [units, setUnits]       = useState<UnitResponse[]>([])
  const [tenants, setTenants]   = useState<TenantRecord[]>([])
  const [leases, setLeases]     = useState<LeaseResponse[]>([])
  const [invoices, setInvoices] = useState<InvoiceResponse[]>([])
  const [jobs, setJobs]         = useState<MaintenanceResponse[]>([])
  const [properties, setProps]  = useState<PropertyResponse[]>([])
  const [loading, setLoading]   = useState(true)

  const [unitModal, setUnitModal]     = useState(false)
  const [tenantModal, setTenantModal] = useState(false)
  const [leaseModal, setLeaseModal]   = useState(false)
  const [busy, setBusy]               = useState(false)

  const [unitForm, setUnitForm]     = useState({ propertyId:'', label:'', unitType:'', bedrooms:'', bathrooms:'', rentAmount:'', depositAmount:'', notes:'' })
  const [tenantForm, setTenantForm] = useState({ fullName:'', phone:'', email:'', nationalId:'', emergencyName:'', emergencyPhone:'' })
  const [leaseForm, setLeaseForm]   = useState({ unitId:'', tenantId:'', startDate:'', endDate:'', rentAmount:'', depositAmount:'', billingDay:'' })
  const [payModal, setPayModal]     = useState<InvoiceResponse|null>(null)
  const [payForm, setPayForm]       = useState({ amount:'', method:'CASH', mpesaReceipt:'', note:'' })
  const [resolveModal, setResolveModal] = useState<MaintenanceResponse|null>(null)
  const [resolveForm, setResolveForm]   = useState({ resolutionNotes:'', cost:'', assignedTo:'' })

  const reload = useCallback(async () => {
    const [s, u, t, l, p, i, m] = await Promise.allSettled([
      pmsApi.units.summary(),
      pmsApi.units.mine({ size: 100 }),
      pmsApi.tenants.mine({ size: 100 }),
      pmsApi.leases.mine({ size: 100 }),
      propertyApi.my(0, 100),
      pmsApi.invoices.mine({ size: 100 }),
      pmsApi.maintenance.mine({ size: 100 }),
    ])
    if (s.status === 'fulfilled') setSummary(s.value)
    if (u.status === 'fulfilled') setUnits(u.value.content || [])
    if (t.status === 'fulfilled') setTenants(t.value.content || [])
    if (l.status === 'fulfilled') setLeases(l.value.content || [])
    if (p.status === 'fulfilled') setProps(p.value.content || [])
    if (i.status === 'fulfilled') setInvoices(i.value.content || [])
    if (m.status === 'fulfilled') setJobs(m.value.content || [])
    setLoading(false)
  }, [])

  useEffect(() => { if (ready) reload() }, [ready, reload])

  const propertyTitle = useMemo(() => {
    const map: Record<string,string> = {}
    for (const p of properties) map[p.id] = p.title
    return map
  }, [properties])

  const vacantUnits = useMemo(() => units.filter(u => u.status === 'VACANT' || u.status === 'RESERVED'), [units])

  const go = (t: Tab) => router.push(`/portfolio?tab=${t}`)

  const addUnit = async () => {
    if (!unitForm.propertyId || !unitForm.label || !unitForm.rentAmount) {
      toast.error('Property, unit name and rent are required.'); return
    }
    setBusy(true)
    try {
      await pmsApi.units.create({
        propertyId: unitForm.propertyId,
        label: unitForm.label,
        unitType: unitForm.unitType || undefined,
        bedrooms: unitForm.bedrooms ? Number(unitForm.bedrooms) : undefined,
        bathrooms: unitForm.bathrooms ? Number(unitForm.bathrooms) : undefined,
        rentAmount: Number(unitForm.rentAmount),
        depositAmount: unitForm.depositAmount ? Number(unitForm.depositAmount) : undefined,
        notes: unitForm.notes || undefined,
      })
      toast.success('Unit added')
      setUnitModal(false)
      setUnitForm({ propertyId:'', label:'', unitType:'', bedrooms:'', bathrooms:'', rentAmount:'', depositAmount:'', notes:'' })
      reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not add that unit.')
    } finally { setBusy(false) }
  }

  const addTenant = async () => {
    if (!tenantForm.fullName || !tenantForm.phone) { toast.error('Name and phone are required.'); return }
    setBusy(true)
    try {
      await pmsApi.tenants.create({
        fullName: tenantForm.fullName,
        phone: tenantForm.phone,
        email: tenantForm.email || undefined,
        nationalId: tenantForm.nationalId || undefined,
        emergencyName: tenantForm.emergencyName || undefined,
        emergencyPhone: tenantForm.emergencyPhone || undefined,
      })
      toast.success('Tenant recorded')
      setTenantModal(false)
      setTenantForm({ fullName:'', phone:'', email:'', nationalId:'', emergencyName:'', emergencyPhone:'' })
      reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not record that tenant.')
    } finally { setBusy(false) }
  }

  const addLease = async () => {
    if (!leaseForm.unitId || !leaseForm.tenantId || !leaseForm.startDate) {
      toast.error('Unit, tenant and start date are required.'); return
    }
    setBusy(true)
    try {
      await pmsApi.leases.create({
        unitId: leaseForm.unitId,
        tenantId: leaseForm.tenantId,
        startDate: leaseForm.startDate,
        endDate: leaseForm.endDate || undefined,
        rentAmount: leaseForm.rentAmount ? Number(leaseForm.rentAmount) : undefined,
        depositAmount: leaseForm.depositAmount ? Number(leaseForm.depositAmount) : undefined,
        billingDay: leaseForm.billingDay ? Number(leaseForm.billingDay) : undefined,
      })
      toast.success('Lease drafted. Activate it when the tenant moves in.')
      setLeaseModal(false)
      setLeaseForm({ unitId:'', tenantId:'', startDate:'', endDate:'', rentAmount:'', depositAmount:'', billingDay:'' })
      go('leases'); reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not draft that lease.')
    } finally { setBusy(false) }
  }

  const leaseAction = async (id: string, action: 'activate'|'end', label: string) => {
    setBusy(true)
    try {
      await pmsApi.leases[action](id)
      toast.success(label)
      reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'That did not work.')
    } finally { setBusy(false) }
  }

  const recordPayment = async () => {
    if (!payModal || !payForm.amount) { toast.error('Enter the amount received.'); return }
    setBusy(true)
    try {
      await pmsApi.invoices.record(payModal.id, {
        amount: Number(payForm.amount),
        method: payForm.method,
        mpesaReceipt: payForm.mpesaReceipt || undefined,
        note: payForm.note || undefined,
      })
      toast.success('Payment recorded')
      setPayModal(null)
      setPayForm({ amount:'', method:'CASH', mpesaReceipt:'', note:'' })
      reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not record that payment.')
    } finally { setBusy(false) }
  }

  const openPay = (inv: InvoiceResponse) => {
    setPayModal(inv)
    setPayForm({ amount: String(inv.balance), method:'CASH', mpesaReceipt:'', note:'' })
  }

  const moveJob = async (job: MaintenanceResponse, status: string) => {
    if (status === 'RESOLVED') {
      setResolveModal(job)
      setResolveForm({ resolutionNotes:'', cost:'', assignedTo: job.assignedTo || '' })
      return
    }
    setBusy(true)
    try {
      await pmsApi.maintenance.update(job.id, { status })
      toast.success('Updated')
      reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not update that request.')
    } finally { setBusy(false) }
  }

  const resolveJob = async () => {
    if (!resolveModal) return
    setBusy(true)
    try {
      await pmsApi.maintenance.update(resolveModal.id, {
        status: 'RESOLVED',
        resolutionNotes: resolveForm.resolutionNotes || undefined,
        cost: resolveForm.cost ? Number(resolveForm.cost) : undefined,
        assignedTo: resolveForm.assignedTo || undefined,
      })
      toast.success('Marked resolved. The tenant has been told.')
      setResolveModal(null)
      reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not resolve that request.')
    } finally { setBusy(false) }
  }

  if (!ready || loading) return <PageLoader/>

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatCard label="Units" value={summary?.totalUnits ?? 0}
          sub={`${summary?.vacantUnits ?? 0} vacant`} icon={<Building2 size={16}/>} color="gold"/>
        <StatCard label="Occupancy" value={`${summary?.occupancyRate ?? 0}%`}
          sub={`${summary?.occupiedUnits ?? 0} occupied`} icon={<DoorOpen size={16}/>} color="emerald"/>
        <StatCard label="Monthly rent roll" value={fmt.currency(summary?.monthlyRentRoll ?? 0)}
          sub={`${summary?.activeLeases ?? 0} active leases`} icon={<FileText size={16}/>} color="blue"/>
        <StatCard label="Outstanding rent" value={fmt.currency(summary?.outstandingRent ?? 0)}
          sub={`${summary?.overdueInvoices ?? 0} overdue · ${summary?.openMaintenance ?? 0} open repairs`}
          icon={<AlertTriangle size={16}/>} color="purple"/>
      </div>

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex gap-1 p-1 rounded-lg bg-gray-100 dark:bg-[#141428]">
          {([['units','Units'],['tenants','Tenants'],['leases','Leases'],['rent','Rent'],['maintenance','Maintenance']] as [Tab,string][]).map(([k,label]) => (
            <button key={k} onClick={() => go(k)}
              className={cn('px-3.5 h-8 rounded-md text-[13px] font-medium transition-colors',
                tab===k ? 'bg-white dark:bg-[#1F1F3D] text-gray-900 dark:text-white shadow-sm'
                        : 'text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200')}>
              {label}
            </button>
          ))}
        </div>
        {tab==='units'   && <Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setUnitModal(true)}>Add unit</Button>}
        {tab==='tenants' && <Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setTenantModal(true)}>Add tenant</Button>}
        {tab==='leases'  && <Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setLeaseModal(true)}>New lease</Button>}
      </div>

      {tab === 'units' && (
        units.length === 0 ? (
          <Card padding="none"><EmptyState icon={<Building2 size={24}/>} title="No units yet"
            desc="Split a property you own into the units you actually rent out — a block of twelve flats is one property and twelve units."
            action={<Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setUnitModal(true)}>Add your first unit</Button>}/></Card>
        ) : (
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {units.map(u => (
              <Card key={u.id} className="flex flex-col gap-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold text-gray-900 dark:text-white truncate">{u.label}</p>
                    <p className="text-[11.5px] text-muted truncate">{propertyTitle[u.propertyId] || 'Property'}</p>
                  </div>
                  <Badge variant={UNIT_STATUS_VARIANT[u.status] ?? 'muted'} size="sm">{readable(u.status)}</Badge>
                </div>
                <p className="text-[15px] font-semibold text-gray-900 dark:text-white tabular-nums">
                  {fmt.currency(u.rentAmount)}<span className="text-[11px] font-normal text-muted"> / month</span>
                </p>
                <div className="flex items-center gap-3 text-[11.5px] text-muted">
                  {u.unitType && <span>{u.unitType}</span>}
                  {u.bedrooms != null && <span>{u.bedrooms} bed</span>}
                  {u.bathrooms != null && <span>{u.bathrooms} bath</span>}
                </div>
                {u.activeTenantName && (
                  <p className="text-[12px] text-gray-600 dark:text-gray-300 flex items-center gap-1.5 pt-1 border-t border-base">
                    <KeyRound size={12} className="text-gold-500"/>{u.activeTenantName}
                  </p>
                )}
              </Card>
            ))}
          </div>
        )
      )}

      {tab === 'tenants' && (
        tenants.length === 0 ? (
          <Card padding="none"><EmptyState icon={<Users size={24}/>} title="No tenants yet"
            desc="Record a tenant from their name and phone number. They do not need a SmartRE account."
            action={<Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setTenantModal(true)}>Add your first tenant</Button>}/></Card>
        ) : (
          <Card padding="none">
            <ul className="divide-y divide-[color:var(--border)]">
              {tenants.map(t => (
                <li key={t.id} className="px-4 py-3.5 flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-gold-500 text-white text-[11px] font-bold flex items-center justify-center shrink-0">
                    {fmt.initials(t.fullName)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-[13.5px] font-medium text-gray-900 dark:text-white truncate">{t.fullName}</p>
                    <p className="text-[11.5px] text-muted truncate">{fmt.phone(t.phone)}{t.email ? ` · ${t.email}` : ''}</p>
                  </div>
                  {t.hasActiveLease
                    ? <Badge variant="success" size="sm">Housed</Badge>
                    : <Badge variant="muted" size="sm">No lease</Badge>}
                </li>
              ))}
            </ul>
          </Card>
        )
      )}

      {tab === 'leases' && (
        leases.length === 0 ? (
          <Card padding="none"><EmptyState icon={<FileText size={24}/>} title="No leases yet"
            desc="A lease ties a tenant to a unit. Draft it first, then activate it when they move in."
            action={<Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setLeaseModal(true)}>Draft a lease</Button>}/></Card>
        ) : (
          <Card padding="none">
            <ul className="divide-y divide-[color:var(--border)]">
              {leases.map(l => (
                <li key={l.id} className="px-4 py-3.5 flex items-start gap-3 flex-wrap">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="text-[13.5px] font-medium text-gray-900 dark:text-white">
                        {l.unitLabel || 'Unit'} · {l.tenantName || 'Tenant'}
                      </p>
                      <Badge variant={LEASE_STATUS_VARIANT[l.status] ?? 'muted'} size="sm">{readable(l.status)}</Badge>
                    </div>
                    <p className="text-[11.5px] text-muted mt-0.5 tabular-nums">
                      {fmt.currency(l.rentAmount)} / month · bills on day {l.billingDay} · from {fmt.date(l.startDate)}
                      {l.endDate ? ` to ${fmt.date(l.endDate)}` : ''}
                    </p>
                    {l.terminatedReason && (
                      <p className="text-[11.5px] text-red-600 dark:text-red-400 mt-1">{l.terminatedReason}</p>
                    )}
                  </div>
                  <div className="flex gap-2">
                    {l.status === 'DRAFT' && (
                      <Button size="sm" loading={busy} onClick={() => leaseAction(l.id, 'activate', 'Lease activated')}>Activate</Button>
                    )}
                    {l.status === 'ACTIVE' && (
                      <Button size="sm" variant="secondary" loading={busy} onClick={() => leaseAction(l.id, 'end', 'Lease ended')}>End</Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        )
      )}

      {tab === 'rent' && (
        invoices.length === 0 ? (
          <Card padding="none"><EmptyState icon={<Receipt size={24}/>} title="No rent invoices yet"
            desc="Invoices are raised automatically each cycle for every active lease, a few days before rent falls due."/></Card>
        ) : (
          <Card padding="none">
            <ul className="divide-y divide-[color:var(--border)]">
              {invoices.map(inv => (
                <li key={inv.id} className="px-4 py-3.5 flex items-start gap-3 flex-wrap">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="text-[13.5px] font-medium text-gray-900 dark:text-white">
                        {inv.unitLabel || 'Unit'} · {inv.tenantName || 'Tenant'}
                      </p>
                      <Badge variant={INVOICE_STATUS_VARIANT[inv.status] ?? 'muted'} size="sm">{readable(inv.status)}</Badge>
                    </div>
                    <p className="text-[11.5px] text-muted mt-0.5 tabular-nums">
                      {inv.invoiceNumber} · {fmt.date(inv.periodStart)} to {fmt.date(inv.periodEnd)} · due {fmt.date(inv.dueDate)}
                    </p>
                    <p className="text-[12px] mt-1 tabular-nums">
                      <span className="font-semibold text-gray-900 dark:text-white">{fmt.currency(inv.amountPaid)}</span>
                      <span className="text-muted"> of {fmt.currency(inv.amountDue)}</span>
                      {inv.balance > 0 && <span className="text-red-600 dark:text-red-400"> · {fmt.currency(inv.balance)} outstanding</span>}
                    </p>
                  </div>
                  {inv.balance > 0 && inv.status !== 'WRITTEN_OFF' && (
                    <Button size="sm" variant="secondary" onClick={() => openPay(inv)}>Record payment</Button>
                  )}
                </li>
              ))}
            </ul>
          </Card>
        )
      )}

      {tab === 'maintenance' && (
        jobs.length === 0 ? (
          <Card padding="none"><EmptyState icon={<Wrench size={24}/>} title="Nothing to fix"
            desc="Repairs your tenants report land here, newest first. You can also log a job yourself against any unit."/></Card>
        ) : (
          <div className="space-y-3">
            {jobs.map(job => (
              <Card key={job.id}>
                <div className="flex items-start justify-between gap-3 flex-wrap">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="text-[14px] font-semibold text-gray-900 dark:text-white">{job.title}</p>
                      <Badge variant={MAINT_STATUS_VARIANT[job.status] ?? 'muted'} size="sm">{readable(job.status)}</Badge>
                      <Badge variant={MAINT_PRIORITY_VARIANT[job.priority] ?? 'muted'} size="sm">{readable(job.priority)}</Badge>
                    </div>
                    <p className="text-[11.5px] text-muted mt-0.5">
                      {job.reference} · {job.unitLabel} · {job.category.toLowerCase()}
                      {job.tenantName ? ` · reported by ${job.tenantName}` : ' · logged by you'} · {fmt.ago(job.createdAt)}
                    </p>
                    <p className="text-[13px] text-gray-700 dark:text-gray-300 mt-2 whitespace-pre-line">{job.description}</p>
                    <MaintenancePhotos requestId={job.id} count={job.imageUrls.length} className="mt-2.5"/>
                    {job.resolutionNotes && (
                      <p className="text-[12px] text-emerald-700 dark:text-emerald-400 mt-2">{job.resolutionNotes}</p>
                    )}
                    {job.cost != null && (
                      <p className="text-[12px] text-muted mt-1 tabular-nums">Cost: {fmt.currency(job.cost)}</p>
                    )}
                  </div>
                  <div className="flex gap-2 flex-wrap">
                    {(NEXT_STATUS[job.status] || []).map(a => (
                      <Button key={a.value} size="sm" variant={a.value==='REJECTED' ? 'ghost' : 'secondary'}
                        loading={busy} onClick={() => moveJob(job, a.value)}>{a.label}</Button>
                    ))}
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )
      )}

      <Modal open={!!resolveModal} onClose={() => setResolveModal(null)} title="Mark this repair resolved"
        footer={<><Button variant="ghost" onClick={() => setResolveModal(null)}>Cancel</Button>
                  <Button loading={busy} onClick={resolveJob}>Mark resolved</Button></>}>
        <div className="space-y-3">
          {resolveModal && <p className="text-[12.5px] text-muted">{resolveModal.reference} — {resolveModal.title}</p>}
          <Textarea label="What was done" rows={3} value={resolveForm.resolutionNotes}
            onChange={e => setResolveForm(f => ({...f, resolutionNotes:e.target.value}))}/>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Cost (KES)" type="number" value={resolveForm.cost}
              onChange={e => setResolveForm(f => ({...f, cost:e.target.value}))}/>
            <Input label="Handled by" placeholder="Plumber, caretaker…" value={resolveForm.assignedTo}
              onChange={e => setResolveForm(f => ({...f, assignedTo:e.target.value}))}/>
          </div>
          <p className="text-[11.5px] text-muted">The tenant is told when you mark this resolved.</p>
        </div>
      </Modal>

      <Modal open={!!payModal} onClose={() => setPayModal(null)} title="Record a rent payment"
        footer={<><Button variant="ghost" onClick={() => setPayModal(null)}>Cancel</Button>
                  <Button loading={busy} onClick={recordPayment}>Record payment</Button></>}>
        <div className="space-y-3">
          {payModal && (
            <p className="text-[12.5px] text-muted">
              {payModal.invoiceNumber} — {fmt.currency(payModal.balance)} outstanding on {payModal.unitLabel}.
            </p>
          )}
          <div className="grid grid-cols-2 gap-3">
            <Input label="Amount received (KES)" type="number" value={payForm.amount}
              onChange={e => setPayForm(f => ({...f, amount:e.target.value}))}/>
            <Select label="How it was paid" value={payForm.method}
              onChange={e => setPayForm(f => ({...f, method:e.target.value}))}
              options={[{value:'CASH',label:'Cash'},{value:'MPESA_PAYBILL',label:'M-Pesa paybill'},{value:'BANK',label:'Bank transfer'}]}/>
          </div>
          <Input label="M-Pesa code" placeholder="QCB7Y2XK91" value={payForm.mpesaReceipt}
            onChange={e => setPayForm(f => ({...f, mpesaReceipt:e.target.value}))}/>
          <Textarea label="Note" rows={2} value={payForm.note}
            onChange={e => setPayForm(f => ({...f, note:e.target.value}))}/>
          <p className="text-[11.5px] text-muted">
            Use this for rent taken outside the app. A tenant with a SmartRE account pays by M-Pesa prompt from their own dashboard.
          </p>
        </div>
      </Modal>

      <Modal open={unitModal} onClose={() => setUnitModal(false)} title="Add a unit"
        footer={<><Button variant="ghost" onClick={() => setUnitModal(false)}>Cancel</Button>
                  <Button loading={busy} onClick={addUnit}>Add unit</Button></>}>
        <div className="space-y-3">
          <Select label="Property" value={unitForm.propertyId} onChange={e => setUnitForm(f => ({...f, propertyId:e.target.value}))}
            options={[{value:'',label:'Select a property you own'}, ...properties.map(p => ({ value:p.id, label:p.title }))]}/>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Unit name" placeholder="A3" value={unitForm.label} onChange={e => setUnitForm(f => ({...f, label:e.target.value}))}/>
            <Input label="Type" placeholder="Bedsitter" value={unitForm.unitType} onChange={e => setUnitForm(f => ({...f, unitType:e.target.value}))}/>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Bedrooms" type="number" value={unitForm.bedrooms} onChange={e => setUnitForm(f => ({...f, bedrooms:e.target.value}))}/>
            <Input label="Bathrooms" type="number" value={unitForm.bathrooms} onChange={e => setUnitForm(f => ({...f, bathrooms:e.target.value}))}/>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Monthly rent (KES)" type="number" value={unitForm.rentAmount} onChange={e => setUnitForm(f => ({...f, rentAmount:e.target.value}))}/>
            <Input label="Deposit (KES)" type="number" value={unitForm.depositAmount} onChange={e => setUnitForm(f => ({...f, depositAmount:e.target.value}))}/>
          </div>
          <Textarea label="Notes" rows={2} value={unitForm.notes} onChange={e => setUnitForm(f => ({...f, notes:e.target.value}))}/>
        </div>
      </Modal>

      <Modal open={tenantModal} onClose={() => setTenantModal(false)} title="Record a tenant"
        footer={<><Button variant="ghost" onClick={() => setTenantModal(false)}>Cancel</Button>
                  <Button loading={busy} onClick={addTenant}>Save tenant</Button></>}>
        <div className="space-y-3">
          <Input label="Full name" value={tenantForm.fullName} onChange={e => setTenantForm(f => ({...f, fullName:e.target.value}))}/>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Phone" placeholder="0712345678" value={tenantForm.phone} onChange={e => setTenantForm(f => ({...f, phone:e.target.value}))}/>
            <Input label="National ID" value={tenantForm.nationalId} onChange={e => setTenantForm(f => ({...f, nationalId:e.target.value}))}/>
          </div>
          <Input label="Email" type="email" value={tenantForm.email} onChange={e => setTenantForm(f => ({...f, email:e.target.value}))}/>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Emergency contact" value={tenantForm.emergencyName} onChange={e => setTenantForm(f => ({...f, emergencyName:e.target.value}))}/>
            <Input label="Emergency phone" value={tenantForm.emergencyPhone} onChange={e => setTenantForm(f => ({...f, emergencyPhone:e.target.value}))}/>
          </div>
        </div>
      </Modal>

      <Modal open={leaseModal} onClose={() => setLeaseModal(false)} title="Draft a lease"
        footer={<><Button variant="ghost" onClick={() => setLeaseModal(false)}>Cancel</Button>
                  <Button loading={busy} onClick={addLease}>Draft lease</Button></>}>
        <div className="space-y-3">
          <Select label="Unit" value={leaseForm.unitId} onChange={e => setLeaseForm(f => ({...f, unitId:e.target.value}))}
            options={[{value:'',label: vacantUnits.length ? 'Select a vacant unit' : 'No vacant units'},
                      ...vacantUnits.map(u => ({ value:u.id, label:`${u.label} — ${fmt.currency(u.rentAmount)}` }))]}/>
          <Select label="Tenant" value={leaseForm.tenantId} onChange={e => setLeaseForm(f => ({...f, tenantId:e.target.value}))}
            options={[{value:'',label: tenants.length ? 'Select a tenant' : 'Record a tenant first'},
                      ...tenants.map(t => ({ value:t.id, label:`${t.fullName} — ${fmt.phone(t.phone)}` }))]}/>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Start date" type="date" value={leaseForm.startDate} onChange={e => setLeaseForm(f => ({...f, startDate:e.target.value}))}/>
            <Input label="End date" type="date" value={leaseForm.endDate} onChange={e => setLeaseForm(f => ({...f, endDate:e.target.value}))}/>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Rent (KES)" type="number" placeholder="Defaults to the unit rent"
              value={leaseForm.rentAmount} onChange={e => setLeaseForm(f => ({...f, rentAmount:e.target.value}))}/>
            <Input label="Deposit (KES)" type="number" placeholder="Defaults to the unit deposit"
              value={leaseForm.depositAmount} onChange={e => setLeaseForm(f => ({...f, depositAmount:e.target.value}))}/>
          </div>
          <Input label="Rent falls due on day" type="number" min={1} max={28} placeholder="Defaults to the start date"
            value={leaseForm.billingDay} onChange={e => setLeaseForm(f => ({...f, billingDay:e.target.value}))}/>
          <p className="text-[11.5px] text-muted">
            A drafted lease changes nothing until you activate it. Activating marks the unit occupied.
          </p>
        </div>
      </Modal>
    </div>
  )
}

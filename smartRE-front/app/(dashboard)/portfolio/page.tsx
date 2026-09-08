'use client'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { Building2, Users, FileText, Plus, DoorOpen, KeyRound, Receipt, AlertTriangle, Wrench, Search, X, Phone, Mail, CreditCard, LifeBuoy, ChevronRight } from 'lucide-react'
import { pmsApi, propertyApi } from '@/lib/api'
import { useAuthGuard } from '@/hooks/useAuthGuard'
import { LANDLORD_ROLES } from '@/lib/roles'
import type { UnitResponse, TenantRecord, LeaseResponse, PortfolioSummaryResponse, PropertyResponse, InvoiceResponse, MaintenanceResponse, CostBearer } from '@/types'
import { Card, StatCard } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Modal, EmptyState, PageLoader } from '@/components/ui/Modal'
import PagedNav from '@/components/ui/Pagination'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import Textarea from '@/components/ui/Textarea'
import MaintenancePhotos from '@/components/property/MaintenancePhotos'
import RentReceipt from '@/components/property/RentReceipt'
import RentInvoiceDocument from '@/components/property/RentInvoiceDocument'
import { cn, fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

type Tab = 'properties' | 'units' | 'tenants' | 'leases' | 'rent' | 'maintenance'

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
  const [resolveForm, setResolveForm]   = useState<{ resolutionNotes:string; cost:string; assignedTo:string; costBorneBy:CostBearer; tenantCharge:string }>(
    { resolutionNotes:'', cost:'', assignedTo:'', costBorneBy:'LANDLORD', tenantCharge:'' })

  /**
   * The units list is paged and filtered on the server, unlike the other collections
   * here. It is the one that grows without bound — a landlord with a hundred properties
   * has several hundred units — and the API caps a page at 100, so fetching "everything"
   * silently returned a subset while the summary card above it stated the real total.
   */
  const UNITS_PER_PAGE = 24
  const [unitPage, setUnitPage] = useState(0)
  const [unitTotal, setUnitTotal] = useState(0)
  const [unitsBusy, setUnitsBusy] = useState(false)

  const reload = useCallback(async () => {
    const [s, u, t, l, p, i, m] = await Promise.allSettled([
      pmsApi.units.summary(),
      pmsApi.units.mine({ size: UNITS_PER_PAGE }),
      pmsApi.tenants.mine({ size: 100 }),
      pmsApi.leases.mine({ size: 100 }),
      propertyApi.my(0, 100),
      pmsApi.invoices.mine({ size: 100 }),
      pmsApi.maintenance.mine({ size: 100 }),
    ])
    if (s.status === 'fulfilled') setSummary(s.value)
    if (u.status === 'fulfilled') { setUnits(u.value.content || []); setUnitTotal(u.value.totalElements ?? 0) }
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

  const [query, setQuery]           = useState('')
  const [statusFilter, setStatus]   = useState<'ALL'|'OCCUPIED'|'VACANT'>('ALL')
  const [locationFilter, setLoc]    = useState('ALL')

  /**
   * Units, fetched for the current filters and page.
   *
   * Debounced because this now reaches the database on every keystroke rather than
   * filtering an array already in memory. 300ms is long enough that typing "Riverside"
   * is one query rather than nine, and short enough to feel immediate.
   */
  useEffect(() => {
    if (!ready) return
    const handle = setTimeout(async () => {
      setUnitsBusy(true)
      try {
        const res = await pmsApi.units.mine({
          propertyId: locationFilter === 'ALL' ? undefined : locationFilter,
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          q: query.trim() || undefined,
          page: unitPage,
          size: UNITS_PER_PAGE,
        })
        setUnits(res.content || [])
        setUnitTotal(res.totalElements ?? 0)
      } finally {
        setUnitsBusy(false)
      }
    }, query.trim() ? 300 : 0)
    return () => clearTimeout(handle)
  }, [ready, query, statusFilter, locationFilter, unitPage])

  // Any filter change puts you back on the first page. Staying on page 7 of a result set
  // that now has two pages shows an empty list and looks like a bug.
  useEffect(() => { setUnitPage(0) }, [query, statusFilter, locationFilter])

  const filteredUnits = units

  const filtersActive = query.trim() !== '' || statusFilter !== 'ALL' || locationFilter !== 'ALL'
  const clearFilters = () => { setQuery(''); setStatus('ALL'); setLoc('ALL') }

  const [tenantDetail, setTenantDetail] = useState<TenantRecord | null>(null)
  const [jobDetail, setJobDetail]       = useState<MaintenanceResponse | null>(null)

  const [jobQuery, setJobQuery]   = useState('')
  const [jobScope, setJobScope]   = useState<'OPEN'|'RESOLVED'|'ALL'>('OPEN')
  const [receiptFor, setReceiptFor] = useState<InvoiceResponse | null>(null)
  const [invoiceFor, setInvoiceFor] = useState<InvoiceResponse | null>(null)

  /**
   * Maintenance history, filtered.
   *
   * Defaults to OPEN because that is the working view — what still needs doing. The
   * resolved ones are never deleted (the service has no delete at all), so RESOLVED
   * and ALL are a record rather than an archive: a landlord asked six months later
   * what was done about the damp needs to be able to answer.
   */
  const visibleJobs = useMemo(() => {
    const q = jobQuery.trim().toLowerCase()
    const done = (st: string) => st === 'RESOLVED' || st === 'CLOSED' || st === 'REJECTED'
    return jobs.filter(j => {
      if (jobScope === 'OPEN'     && done(j.status)) return false
      if (jobScope === 'RESOLVED' && !done(j.status)) return false
      if (!q) return true
      return [j.title, j.description, j.reference, j.unitLabel, j.category, j.tenantName, j.assignedTo]
        .some(f => f?.toLowerCase().includes(q))
    })
  }, [jobs, jobQuery, jobScope])

  /**
   * Every property the landlord manages, with what it is actually doing.
   *
   * Built from properties rather than from units, so a property with no units yet
   * still appears — that is precisely the one needing attention, and grouping units
   * would hide it. The rent roll counts let units only; the asking rent of an empty
   * flat is not income.
   */
  const managedProperties = useMemo(() => {
    return properties.map(pr => {
      const own = units.filter(u => u.propertyId === pr.id)
      const occupied = own.filter(u => u.status === 'OCCUPIED')
      const openJobs = jobs.filter(j =>
        own.some(u => u.id === j.unitId) && j.status !== 'RESOLVED' && j.status !== 'REJECTED')
      return {
        property: pr,
        unitCount: own.length,
        occupied: occupied.length,
        vacant: own.length - occupied.length,
        occupancy: own.length ? Math.round((occupied.length / own.length) * 100) : 0,
        rentRoll: occupied.reduce((sum, u) => sum + (Number(u.rentAmount) || 0), 0),
        openJobs: openJobs.length,
      }
    }).sort((a, b) => a.property.title.localeCompare(b.property.title))
  }, [properties, units, jobs])

  /**
   * Everything the landlord already holds about the open tenant.
   *
   * Assembled from records that are already loaded rather than fetched again: the
   * question "who is this and what is their situation" is answered by their lease,
   * their unit and their open repairs, and all three are on the page already. A
   * second round of requests to render a panel would be slower and no more correct.
   */
  const tenantContext = useMemo(() => {
    if (!tenantDetail) return null
    const lease = leases.find(l => l.tenantId === tenantDetail.id && l.status === 'ACTIVE')
                  ?? leases.find(l => l.tenantId === tenantDetail.id)
    const unit = lease ? units.find(u => u.id === lease.unitId) : undefined
    return {
      lease,
      unit,
      property: unit ? propertyTitle[unit.propertyId] : undefined,
      openJobs: unit ? jobs.filter(m => m.unitId === unit.id && m.status !== 'RESOLVED' && m.status !== 'REJECTED') : [],
      unpaid: invoices.filter(i => i.tenantId === tenantDetail.id && i.status !== 'PAID'),
    }
  }, [tenantDetail, leases, units, invoices, jobs, propertyTitle])

  /**
   * Units grouped by the property they belong to.
   *
   * A landlord with one block does not need this. A landlord with six does: a flat
   * list interleaves Kilimani and Westlands, and the only aggregate on the page is a
   * blended occupancy figure that cannot tell you which building is the problem. So
   * each location carries its own count, occupancy and rent roll.
   */
  const unitsByProperty = useMemo(() => {
    const groups: Record<string, typeof units> = {}
    for (const u of filteredUnits) (groups[u.propertyId] ??= []).push(u)

    return Object.entries(groups)
      .map(([propertyId, list]) => {
        const occupied = list.filter(u => u.status === 'OCCUPIED').length
        return {
          propertyId,
          title: propertyTitle[propertyId] || 'Property',
          units: [...list].sort((a, b) => a.label.localeCompare(b.label)),
          occupied,
          vacant: list.length - occupied,
          // Rent roll counts what is actually let, not the asking rent of empty units.
          rentRoll: list.filter(u => u.status === 'OCCUPIED')
                        .reduce((sum, u) => sum + (Number(u.rentAmount) || 0), 0),
          occupancy: list.length ? Math.round((occupied / list.length) * 100) : 0,
        }
      })
      .sort((a, b) => a.title.localeCompare(b.title))
  }, [filteredUnits, propertyTitle])

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

  /**
   * Attaches a tenant record to the SmartRE account registered under the same email, so
   * the tenant can see their own invoices and raise repairs. The landlord supplies
   * nothing here: the server resolves the account from the email already on the record,
   * which is what stops a landlord attaching an arbitrary account to a record they own.
   */
  const linkTenantAccount = async (tenant: TenantRecord) => {
    setBusy(true)
    try {
      const updated = await pmsApi.tenants.linkUser(tenant.id)
      setTenantDetail(updated)
      toast.success(`${tenant.fullName} can now sign in and see this tenancy.`)
      reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not link that account.')
    } finally { setBusy(false) }
  }

  const unlinkTenantAccount = async (tenant: TenantRecord) => {
    setBusy(true)
    try {
      const updated = await pmsApi.tenants.unlinkUser(tenant.id)
      setTenantDetail(updated)
      toast.success('Account detached. The tenancy record and its history are unchanged.')
      reload()
    } catch (e:any) {
      toast.error(e?.response?.data?.error || 'Could not detach that account.')
    } finally { setBusy(false) }
  }

  const moveJob = async (job: MaintenanceResponse, status: string) => {
    if (status === 'RESOLVED') {
      setResolveModal(job)
      setResolveForm({ resolutionNotes:'', cost:'', assignedTo: job.assignedTo || '',
                       costBorneBy: job.costBorneBy || 'LANDLORD',
                       tenantCharge: job.tenantCharge != null ? String(job.tenantCharge) : '' })
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
      const cost = resolveForm.cost ? Number(resolveForm.cost) : undefined
      await pmsApi.maintenance.update(resolveModal.id, {
        status: 'RESOLVED',
        resolutionNotes: resolveForm.resolutionNotes || undefined,
        cost,
        assignedTo: resolveForm.assignedTo || undefined,
        // A job that cost nothing has nobody to bill, so leave the attribution unset
        // rather than recording a meaningless LANDLORD against a zero.
        costBorneBy: cost ? resolveForm.costBorneBy : undefined,
        // The server derives the tenant's share for LANDLORD (none) and TENANT (all of
        // it); only a split needs a figure from us.
        tenantCharge: cost && resolveForm.costBorneBy === 'SHARED' && resolveForm.tenantCharge
          ? Number(resolveForm.tenantCharge) : undefined,
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
    <div className="space-y-4">
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
          {([['properties','Properties'],['units','Units'],['tenants','Tenants'],['leases','Leases'],['rent','Rent'],['maintenance','Maintenance']] as [Tab,string][]).map(([k,label]) => (
            <button key={k} onClick={() => go(k)}
              className={cn('px-3.5 h-8 rounded-md text-base font-medium transition-colors',
                tab===k ? 'bg-white dark:bg-[#1F1F3D] text-gray-900 dark:text-white shadow-sm'
                        : 'text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200')}>
              {label}
            </button>
          ))}
        </div>
        {tab==='properties' && <Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => router.push('/properties/new')}>Add property</Button>}
        {tab==='units'   && <Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setUnitModal(true)}>Add unit</Button>}
        {tab==='tenants' && <Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setTenantModal(true)}>Add tenant</Button>}
        {tab==='leases'  && <Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setLeaseModal(true)}>New lease</Button>}
      </div>

      {tab === 'properties' && (
        managedProperties.length === 0 ? (
          <Card padding="none"><EmptyState icon={<Building2 size={24}/>} title="No properties yet"
            desc="A property is the building or plot. Add one, then split it into the units you actually rent out."
            action={<Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => router.push('/properties/new')}>Add a property</Button>}/></Card>
        ) : (
          <Card padding="none">
            <ul className="divide-y divide-[color:var(--border)]">
              {managedProperties.map(m => (
                <li key={m.property.id}>
                  <button onClick={() => { setLoc(m.property.id); go('units') }}
                    className="w-full text-left px-3 py-2 flex items-center gap-4 hover:bg-gray-50 dark:hover:bg-white/5 transition-colors">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <p className="text-base font-medium text-gray-900 dark:text-white truncate">{m.property.title}</p>
                        {/* A managed property never went on the marketplace, and saying so
                            here is the only place a landlord can tell the two apart. */}
                        {m.property.status === 'UNLISTED'
                          ? <Badge variant="muted" size="sm">Managed only</Badge>
                          : <Badge variant={m.property.status === 'ACTIVE' ? 'success' : 'warning'} size="sm">
                              {readable(m.property.status)}
                            </Badge>}
                      </div>
                      <p className="text-2xs text-muted truncate">
                        {[m.property.subCounty, m.property.county].filter(Boolean).join(', ')}
                      </p>
                    </div>

                    <div className="hidden sm:flex items-center gap-3.5 text-2xs text-muted tabular-nums whitespace-nowrap">
                      <span className="text-center">
                        <span className="block text-sm font-medium text-gray-900 dark:text-white">{m.unitCount}</span>
                        units
                      </span>
                      <span className="text-center">
                        <span className="block text-sm font-medium text-gray-900 dark:text-white">{m.occupancy}%</span>
                        let
                      </span>
                      <span className="text-center">
                        <span className={cn('block text-sm font-medium',
                          m.vacant > 0 ? 'text-amber-600 dark:text-amber-400' : 'text-gray-900 dark:text-white')}>
                          {m.vacant}
                        </span>
                        vacant
                      </span>
                      <span className="text-center">
                        <span className={cn('block text-sm font-medium',
                          m.openJobs > 0 ? 'text-amber-600 dark:text-amber-400' : 'text-gray-900 dark:text-white')}>
                          {m.openJobs}
                        </span>
                        repairs
                      </span>
                      <span className="text-right min-w-[92px]">
                        <span className="block text-sm font-medium text-gray-900 dark:text-white">
                          {fmt.currency(m.rentRoll)}
                        </span>
                        per month
                      </span>
                    </div>

                    <ChevronRight size={13} className="text-gray-300 dark:text-gray-600 shrink-0"/>
                  </button>
                </li>
              ))}
            </ul>
          </Card>
        )
      )}

      {tab === 'units' && units.length > 0 && (
        /* Search, status and location. Kept on one line and at label size, because a
           filter bar that competes with the content it filters is a poor trade. */
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative flex-1 min-w-[180px]">
            <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"/>
            <input
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder="Search unit, tenant or location…"
              aria-label="Search units"
              className="w-full pl-7 pr-7 py-1.5 rounded-lg border border-base bg-white dark:bg-[#201911]
                         text-sm text-gray-900 dark:text-gray-100 placeholder:text-gray-400
                         focus:outline-none focus:ring-1 focus:ring-gold-400"
            />
            {query && (
              <button onClick={() => setQuery('')} aria-label="Clear search"
                className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-700 dark:hover:text-gray-200">
                <X size={12}/>
              </button>
            )}
          </div>

          <div className="flex items-center gap-0.5 p-0.5 rounded-lg bg-gray-100 dark:bg-white/5">
            {(['ALL','OCCUPIED','VACANT'] as const).map(s => (
              <button key={s} onClick={() => setStatus(s)}
                className={cn('px-2.5 py-1 rounded-md text-2xs font-medium transition-colors',
                  statusFilter === s
                    ? 'bg-white dark:bg-[#2A2118] text-gray-900 dark:text-white shadow-sm'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200')}>
                {s === 'ALL' ? 'All' : s === 'OCCUPIED' ? 'Let' : 'Vacant'}
              </button>
            ))}
          </div>

          {/* Only worth showing once there is more than one place to choose between. */}
          {properties.length > 1 && (
            <select
              value={locationFilter}
              onChange={e => setLoc(e.target.value)}
              aria-label="Filter by location"
              className="py-1.5 pl-2.5 pr-7 rounded-lg border border-base bg-white dark:bg-[#201911]
                         text-2xs text-gray-700 dark:text-gray-200 focus:outline-none focus:ring-1 focus:ring-gold-400">
              <option value="ALL">All locations</option>
              {properties.map(pr => <option key={pr.id} value={pr.id}>{pr.title}</option>)}
            </select>
          )}

          {filtersActive && (
            <button onClick={clearFilters}
              className="text-2xs text-muted hover:text-gray-800 dark:hover:text-gray-200 px-1.5">
              Clear
            </button>
          )}

          <span className="text-2xs text-muted tabular-nums ml-auto" aria-live="polite">
            {unitTotal.toLocaleString()} {unitTotal === 1 ? 'unit' : 'units'}
            {filtersActive ? ' match' : ''}
          </span>
        </div>
      )}

      {tab === 'units' && (
        unitTotal === 0 && !filtersActive ? (
          <Card padding="none"><EmptyState icon={<Building2 size={24}/>} title="No units yet"
            desc="Split a property you own into the units you actually rent out — a block of twelve flats is one property and twelve units."
            action={<Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setUnitModal(true)}>Add your first unit</Button>}/></Card>
        ) : filteredUnits.length === 0 ? (
          <Card padding="none"><EmptyState icon={<Search size={22}/>} title="Nothing matches"
            desc="No unit matches that search or those filters."
            action={<Button size="sm" variant="ghost" onClick={clearFilters}>Clear filters</Button>}/></Card>
        ) : (
          <div className="flex flex-col gap-3.5">
            {unitsByProperty.map(group => (
              <section key={group.propertyId}>
                {/* One header per location. Shown even for a single property, so the
                    layout does not change shape the day a second one is added. */}
                <div className="flex items-center justify-between gap-3 mb-2 pb-1.5 border-b border-base">
                  <div className="flex items-baseline gap-2 min-w-0">
                    <h3 className="text-sm font-semibold text-gray-900 dark:text-white truncate">{group.title}</h3>
                    <span className="text-2xs text-muted whitespace-nowrap">
                      {group.units.length} {group.units.length === 1 ? 'unit' : 'units'}
                    </span>
                  </div>
                  <div className="flex items-center gap-3 text-2xs text-muted whitespace-nowrap tabular-nums">
                    <span>{group.occupancy}% let</span>
                    <span className="text-gray-300 dark:text-gray-700">·</span>
                    <span>{group.vacant} vacant</span>
                    <span className="text-gray-300 dark:text-gray-700">·</span>
                    <span className="font-medium text-gray-700 dark:text-gray-300">{fmt.currency(group.rentRoll)}/mo</span>
                  </div>
                </div>

                <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {group.units.map(u => (
              <Card key={u.id} className="flex flex-col gap-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-gray-900 dark:text-white truncate">{u.label}</p>
                  </div>
                  <Badge variant={UNIT_STATUS_VARIANT[u.status] ?? 'muted'} size="sm">{readable(u.status)}</Badge>
                </div>
                <p className="text-base font-semibold text-gray-900 dark:text-white tabular-nums">
                  {fmt.currency(u.rentAmount)}<span className="text-2xs font-normal text-muted"> / month</span>
                </p>
                <div className="flex items-center gap-3 text-2xs text-muted">
                  {u.unitType && <span>{u.unitType}</span>}
                  {u.bedrooms != null && <span>{u.bedrooms} bed</span>}
                  {u.bathrooms != null && <span>{u.bathrooms} bath</span>}
                </div>
                {u.activeTenantName && (
                  <p className="text-sm text-gray-600 dark:text-gray-300 flex items-center gap-1.5 pt-1 border-t border-base">
                    <KeyRound size={12} className="text-gold-500"/>{u.activeTenantName}
                  </p>
                )}
              </Card>
            ))}
                </div>
              </section>
            ))}

            {/* The control states the range and the total, because with several hundred
                units the number that matters is the one not on screen. */}
            <Card padding="none">
              <PagedNav page={unitPage} size={UNITS_PER_PAGE} total={unitTotal}
                        onPage={setUnitPage} busy={unitsBusy}/>
            </Card>
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
                <li key={t.id}>
                 <button onClick={() => setTenantDetail(t)}
                   className="w-full text-left px-3 py-2.5 flex items-center gap-3 hover:bg-gray-50 dark:hover:bg-white/5 transition-colors">
                  <div className="w-8 h-8 rounded-full bg-gold-500 text-white text-xs font-bold flex items-center justify-center shrink-0">
                    {fmt.initials(t.fullName)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-base font-medium text-gray-900 dark:text-white truncate">{t.fullName}</p>
                    <p className="text-2xs text-muted truncate">{fmt.phone(t.phone)}{t.email ? ` · ${t.email}` : ''}</p>
                  </div>
                  {t.hasActiveLease
                    ? <Badge variant="success" size="sm">Housed</Badge>
                    : <Badge variant="muted" size="sm">No lease</Badge>}
                  <ChevronRight size={13} className="text-gray-300 dark:text-gray-600 shrink-0"/>
                 </button>
                </li>
              ))}
            </ul>
          </Card>
        )
      )}

      {/* Maintenance detail. The list shows what needs doing; this shows what has
          happened — who reported it, when each step was taken, what it cost. The
          timeline is the part that matters in a dispute, and none of it was visible
          from the list. */}
      <Modal open={!!jobDetail} onClose={() => setJobDetail(null)}
             title={jobDetail?.title ?? 'Request'}>
        {jobDetail && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={MAINT_STATUS_VARIANT[jobDetail.status] ?? 'muted'} size="sm">
                {readable(jobDetail.status)}
              </Badge>
              <Badge variant={MAINT_PRIORITY_VARIANT[jobDetail.priority] ?? 'muted'} size="sm">
                {readable(jobDetail.priority)}
              </Badge>
              <span className="text-2xs text-muted font-mono">{jobDetail.reference}</span>
            </div>

            <div>
              <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">Where</p>
              <p className="text-sm text-gray-700 dark:text-gray-200">
                Unit {jobDetail.unitLabel ?? '—'}
                {(() => {
                  const u = units.find(x => x.id === jobDetail.unitId)
                  return u ? ` · ${propertyTitle[u.propertyId] ?? ''}` : ''
                })()}
                {' · '}{readable(jobDetail.category)}
              </p>
              <p className="text-2xs text-muted mt-0.5">
                Reported by {jobDetail.raisedByRole === 'TENANT'
                  ? (jobDetail.tenantName ?? 'the tenant')
                  : 'you'} · {fmt.ago(jobDetail.createdAt)}
              </p>
            </div>

            <div>
              <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">What they said</p>
              <p className="text-sm text-gray-700 dark:text-gray-200 whitespace-pre-wrap">{jobDetail.description}</p>
            </div>

            {jobDetail.imageUrls?.length > 0 && (
              <div>
                <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">
                  Photos ({jobDetail.imageUrls.length})
                </p>
                <MaintenancePhotos requestId={jobDetail.id} count={jobDetail.imageUrls.length}/>
              </div>
            )}

            <div>
              <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">Timeline</p>
              <ul className="space-y-1.5">
                {([
                  ['Reported',     jobDetail.createdAt],
                  ['Acknowledged', jobDetail.acknowledgedAt],
                  ['Resolved',     jobDetail.resolvedAt],
                  ['Closed',       jobDetail.closedAt],
                ] as [string, string | undefined][])
                  // Only steps that actually happened. Rendering empty rows for future
                  // states would imply a schedule nobody committed to.
                  .filter(([, at]) => !!at)
                  .map(([label, at]) => (
                    <li key={label} className="flex items-center gap-2.5 text-sm">
                      <span className="w-1.5 h-1.5 rounded-full bg-gold-500 shrink-0"/>
                      <span className="text-gray-700 dark:text-gray-200">{label}</span>
                      <span className="ml-auto text-2xs text-muted tabular-nums">{fmt.date(at!)}</span>
                    </li>
                  ))}
              </ul>
            </div>

            {(jobDetail.resolutionNotes || jobDetail.cost != null || jobDetail.assignedTo) && (
              <div>
                <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">Resolution</p>
                {jobDetail.assignedTo && (
                  <p className="text-sm text-gray-700 dark:text-gray-200">Handled by {jobDetail.assignedTo}</p>
                )}
                {jobDetail.resolutionNotes && (
                  <p className="text-sm text-gray-700 dark:text-gray-200 whitespace-pre-wrap mt-1">
                    {jobDetail.resolutionNotes}
                  </p>
                )}
                {jobDetail.cost != null && (
                  <p className="text-sm text-gray-900 dark:text-white font-medium tabular-nums mt-1">
                    {fmt.currency(jobDetail.cost)}
                  </p>
                )}
                {jobDetail.costBorneBy && (
                  <p className="text-2xs text-muted mt-1">
                    {jobDetail.costBorneBy === 'LANDLORD'
                      ? 'Borne by you — the tenant was not charged.'
                      : jobDetail.costBorneBy === 'TENANT'
                        ? `Charged to the tenant in full.`
                        : `Split — ${fmt.currency(jobDetail.tenantCharge ?? 0)} charged to the tenant.`}
                  </p>
                )}
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Tenant detail. A landlord opening a tenant is asking one of three things:
          how do I reach them, where are they and on what terms, and do they owe me
          anything. The panel answers those in that order and stops. */}
      <Modal open={!!tenantDetail} onClose={() => setTenantDetail(null)}
             title={tenantDetail?.fullName ?? 'Tenant'}>
        {tenantDetail && tenantContext && (
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-gold-500 text-white text-sm font-bold flex items-center justify-center shrink-0">
                {fmt.initials(tenantDetail.fullName)}
              </div>
              <div className="min-w-0">
                <p className="text-base font-semibold text-gray-900 dark:text-white truncate">{tenantDetail.fullName}</p>
                <p className="text-2xs text-muted">
                  {tenantDetail.hasActiveLease ? 'Currently housed' : 'No active lease'}
                  {tenantDetail.userId ? ' · has a SmartRE account' : ' · no account linked'}
                </p>
              </div>
            </div>

            {/* Linking is what turns a row in the landlord's book into a person who can
                sign in, see their own invoices and report a repair. Without an email on
                the record there is nothing to match against, so say that rather than
                offering a button that can only fail. */}
            <div className="rounded-lg border border-base p-3">
              {tenantDetail.userId ? (
                <div className="flex items-center justify-between gap-3">
                  <p className="text-2xs text-muted">
                    Signed in as a SmartRE tenant — they can see their invoices and raise repairs.
                  </p>
                  <Button size="sm" variant="ghost" loading={busy}
                    onClick={() => unlinkTenantAccount(tenantDetail)}>Detach</Button>
                </div>
              ) : tenantDetail.email ? (
                <div className="flex items-center justify-between gap-3">
                  <p className="text-2xs text-muted">
                    Link the account registered as {tenantDetail.email} so they can see this tenancy.
                  </p>
                  <Button size="sm" loading={busy}
                    onClick={() => linkTenantAccount(tenantDetail)}>Link account</Button>
                </div>
              ) : (
                <p className="text-2xs text-muted">
                  Add an email address to this tenant before linking a SmartRE account.
                </p>
              )}
            </div>

            <div>
              <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">Contact</p>
              <div className="space-y-1.5">
                <a href={`tel:${tenantDetail.phone}`} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-200 hover:text-gold-600">
                  <Phone size={12} className="text-gray-400 shrink-0"/>{fmt.phone(tenantDetail.phone)}
                </a>
                {tenantDetail.email && (
                  <a href={`mailto:${tenantDetail.email}`} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-200 hover:text-gold-600 truncate">
                    <Mail size={12} className="text-gray-400 shrink-0"/>{tenantDetail.email}
                  </a>
                )}
                {tenantDetail.nationalId && (
                  <p className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-200">
                    <CreditCard size={12} className="text-gray-400 shrink-0"/>ID {tenantDetail.nationalId}
                  </p>
                )}
                {tenantDetail.emergencyName && (
                  <p className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-200">
                    <LifeBuoy size={12} className="text-gray-400 shrink-0"/>
                    {tenantDetail.emergencyName}
                    {tenantDetail.emergencyPhone ? ` · ${fmt.phone(tenantDetail.emergencyPhone)}` : ''}
                  </p>
                )}
              </div>
            </div>

            <div>
              <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">Tenancy</p>
              {tenantContext.lease && tenantContext.unit ? (
                <div className="rounded-lg border border-base p-3 space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-medium text-gray-900 dark:text-white">
                      {tenantContext.unit.label} · {tenantContext.property}
                    </p>
                    <Badge variant={tenantContext.lease.status === 'ACTIVE' ? 'success' : 'muted'} size="sm">
                      {readable(tenantContext.lease.status)}
                    </Badge>
                  </div>
                  <p className="text-2xs text-muted tabular-nums">
                    {fmt.currency(tenantContext.lease.rentAmount)} / month · bills on day {tenantContext.lease.billingDay} · from {fmt.date(tenantContext.lease.startDate)}
                  </p>
                </div>
              ) : (
                <p className="text-sm text-muted">Not currently in a unit.</p>
              )}
            </div>

            <div>
              <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">Standing</p>
              <div className="grid grid-cols-2 gap-2">
                <div className="rounded-lg border border-base p-2.5">
                  <p className="text-2xs text-muted">Unpaid invoices</p>
                  <p className="text-base font-semibold text-gray-900 dark:text-white tabular-nums">
                    {tenantContext.unpaid.length}
                  </p>
                </div>
                <div className="rounded-lg border border-base p-2.5">
                  <p className="text-2xs text-muted">Open repairs</p>
                  <p className="text-base font-semibold text-gray-900 dark:text-white tabular-nums">
                    {tenantContext.openJobs.length}
                  </p>
                </div>
              </div>
              {tenantContext.openJobs.length > 0 && (
                <ul className="mt-2 space-y-1">
                  {tenantContext.openJobs.slice(0, 3).map(j => (
                    <li key={j.id} className="flex items-center gap-2 text-2xs text-muted">
                      <Wrench size={11} className="text-gray-400 shrink-0"/>
                      <span className="truncate">{j.title}</span>
                      <span className="ml-auto">{readable(j.status)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}
      </Modal>

      {tab === 'leases' && (
        leases.length === 0 ? (
          <Card padding="none"><EmptyState icon={<FileText size={24}/>} title="No leases yet"
            desc="A lease ties a tenant to a unit. Draft it first, then activate it when they move in."
            action={<Button size="sm" leftIcon={<Plus size={14}/>} onClick={() => setLeaseModal(true)}>Draft a lease</Button>}/></Card>
        ) : (
          <Card padding="none">
            <ul className="divide-y divide-[color:var(--border)]">
              {leases.map(l => (
                <li key={l.id} className="px-3 py-2.5 flex items-start gap-3 flex-wrap">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="text-base font-medium text-gray-900 dark:text-white">
                        {l.unitLabel || 'Unit'} · {l.tenantName || 'Tenant'}
                      </p>
                      <Badge variant={LEASE_STATUS_VARIANT[l.status] ?? 'muted'} size="sm">{readable(l.status)}</Badge>
                    </div>
                    <p className="text-2xs text-muted mt-0.5 tabular-nums">
                      {fmt.currency(l.rentAmount)} / month · bills on day {l.billingDay} · from {fmt.date(l.startDate)}
                      {l.endDate ? ` to ${fmt.date(l.endDate)}` : ''}
                    </p>
                    {l.terminatedReason && (
                      <p className="text-xs text-red-600 dark:text-red-400 mt-1">{l.terminatedReason}</p>
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
                <li key={inv.id} className="px-3 py-2.5 flex items-start gap-3 flex-wrap">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="text-base font-medium text-gray-900 dark:text-white">
                        {inv.unitLabel || 'Unit'} · {inv.tenantName || 'Tenant'}
                      </p>
                      <Badge variant={INVOICE_STATUS_VARIANT[inv.status] ?? 'muted'} size="sm">{readable(inv.status)}</Badge>
                    </div>
                    <p className="text-2xs text-muted mt-0.5 tabular-nums">
                      {inv.invoiceNumber} · {fmt.date(inv.periodStart)} to {fmt.date(inv.periodEnd)} · due {fmt.date(inv.dueDate)}
                    </p>
                    <p className="text-sm mt-1 tabular-nums">
                      <span className="font-semibold text-gray-900 dark:text-white">{fmt.currency(inv.amountPaid)}</span>
                      <span className="text-muted"> of {fmt.currency(inv.amountDue)}</span>
                      {inv.balance > 0 && <span className="text-red-600 dark:text-red-400"> · {fmt.currency(inv.balance)} outstanding</span>}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button size="sm" variant="ghost" onClick={() => setInvoiceFor(inv)}>Invoice</Button>
                    {inv.amountPaid > 0 && (
                      <Button size="sm" variant="ghost" onClick={() => setReceiptFor(inv)}>Receipt</Button>
                    )}
                    {inv.balance > 0 && inv.status !== 'WRITTEN_OFF' && (
                      <Button size="sm" variant="secondary" onClick={() => openPay(inv)}>Record payment</Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        )
      )}

      {tab === 'maintenance' && jobs.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative flex-1 min-w-[180px]">
            <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"/>
            <input
              value={jobQuery}
              onChange={e => setJobQuery(e.target.value)}
              placeholder="Search title, reference, unit, tenant or contractor…"
              aria-label="Search repairs"
              className="w-full pl-7 pr-7 py-1.5 rounded-lg border border-base bg-white dark:bg-[#201911]
                         text-sm text-gray-900 dark:text-gray-100 placeholder:text-gray-400
                         focus:outline-none focus:ring-1 focus:ring-gold-400"
            />
            {jobQuery && (
              <button onClick={() => setJobQuery('')} aria-label="Clear search"
                className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-700 dark:hover:text-gray-200">
                <X size={12}/>
              </button>
            )}
          </div>

          {/* Defaults to Open — the working view. Resolved is kept as a record, never
              deleted, for the conversation six months later about what was done. */}
          <div className="flex items-center gap-0.5 p-0.5 rounded-lg bg-gray-100 dark:bg-white/5">
            {(['OPEN','RESOLVED','ALL'] as const).map(sc => (
              <button key={sc} onClick={() => setJobScope(sc)}
                className={cn('px-2.5 py-1 rounded-md text-2xs font-medium transition-colors',
                  jobScope === sc
                    ? 'bg-white dark:bg-[#2A2118] text-gray-900 dark:text-white shadow-sm'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200')}>
                {sc === 'OPEN' ? 'Open' : sc === 'RESOLVED' ? 'History' : 'All'}
              </button>
            ))}
          </div>

          <span className="text-2xs text-muted tabular-nums ml-auto">
            {visibleJobs.length} of {jobs.length}
          </span>
        </div>
      )}

      {tab === 'maintenance' && (
        jobs.length === 0 ? (
          <Card padding="none"><EmptyState icon={<Wrench size={24}/>} title="Nothing to fix"
            desc="Repairs your tenants report land here, newest first. You can also log a job yourself against any unit."/></Card>
        ) : (
          <div className="space-y-3">
            {visibleJobs.map(job => (
              <Card key={job.id}>
                <div className="flex items-start justify-between gap-3 flex-wrap">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <button onClick={() => setJobDetail(job)}
                        className="text-sm font-semibold text-gray-900 dark:text-white text-left hover:text-gold-600 dark:hover:text-gold-400 transition-colors">
                        {job.title}
                      </button>
                      <Badge variant={MAINT_STATUS_VARIANT[job.status] ?? 'muted'} size="sm">{readable(job.status)}</Badge>
                      <Badge variant={MAINT_PRIORITY_VARIANT[job.priority] ?? 'muted'} size="sm">{readable(job.priority)}</Badge>
                    </div>
                    <p className="text-2xs text-muted mt-0.5">
                      {job.reference} · {job.unitLabel} · {job.category.toLowerCase()}
                      {job.tenantName ? ` · reported by ${job.tenantName}` : ' · logged by you'} · {fmt.ago(job.createdAt)}
                    </p>
                    <p className="text-base text-gray-700 dark:text-gray-300 mt-2 whitespace-pre-line">{job.description}</p>
                    <MaintenancePhotos requestId={job.id} count={job.imageUrls.length} className="mt-2.5"/>
                    {job.resolutionNotes && (
                      <p className="text-sm text-emerald-700 dark:text-emerald-400 mt-2">{job.resolutionNotes}</p>
                    )}
                    {job.cost != null && (
                      <p className="text-sm text-muted mt-1 tabular-nums">Cost: {fmt.currency(job.cost)}</p>
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
          {resolveModal && <p className="text-sm text-muted">{resolveModal.reference} — {resolveModal.title}</p>}
          <Textarea label="What was done" rows={3} value={resolveForm.resolutionNotes}
            onChange={e => setResolveForm(f => ({...f, resolutionNotes:e.target.value}))}/>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Cost (KES)" type="number" value={resolveForm.cost}
              onChange={e => setResolveForm(f => ({...f, cost:e.target.value}))}/>
            <Input label="Handled by" placeholder="Plumber, caretaker…" value={resolveForm.assignedTo}
              onChange={e => setResolveForm(f => ({...f, assignedTo:e.target.value}))}/>
          </div>

          {/* Only meaningful once there is a cost: attributing nothing to somebody is
              noise in the tenant's statement. */}
          {resolveForm.cost && Number(resolveForm.cost) > 0 && (
            <div className="space-y-3 rounded-lg border border-default p-3">
              <Select label="Who pays for this?" value={resolveForm.costBorneBy}
                onChange={e => setResolveForm(f => ({...f, costBorneBy: e.target.value as CostBearer}))}
                options={[
                  { value:'LANDLORD', label:'You — normal wear, or your responsibility' },
                  { value:'TENANT',   label:'The tenant — damage or their responsibility' },
                  { value:'SHARED',   label:'Split between you' },
                ]}/>

              {resolveForm.costBorneBy === 'SHARED' && (
                <Input label="Tenant's share (KES)" type="number" value={resolveForm.tenantCharge}
                  onChange={e => setResolveForm(f => ({...f, tenantCharge:e.target.value}))}
                  hint="Cannot be more than the repair cost."/>
              )}

              <p className="text-2xs text-muted">
                {resolveForm.costBorneBy === 'LANDLORD'
                  ? 'The tenant is charged nothing.'
                  : resolveForm.costBorneBy === 'TENANT'
                    ? `The tenant is charged the full ${fmt.currency(Number(resolveForm.cost))}.`
                    : 'You are charged the remainder.'}
              </p>
            </div>
          )}

          <p className="text-2xs text-muted">The tenant is told when you mark this resolved.</p>
        </div>
      </Modal>

      <RentInvoiceDocument
        invoice={invoiceFor}
        propertyName={(() => {
          const unit = invoiceFor ? units.find(u => u.id === invoiceFor.unitId) : undefined
          return unit ? propertyTitle[unit.propertyId] : undefined
        })()}
        onClose={() => setInvoiceFor(null)}/>

      <RentReceipt
        invoice={receiptFor}
        propertyName={(() => {
          const unit = receiptFor ? units.find(u => u.id === receiptFor.unitId) : undefined
          return unit ? propertyTitle[unit.propertyId] : undefined
        })()}
        onClose={() => setReceiptFor(null)}/>

      <Modal open={!!payModal} onClose={() => setPayModal(null)} title="Record a rent payment"
        footer={<><Button variant="ghost" onClick={() => setPayModal(null)}>Cancel</Button>
                  <Button loading={busy} onClick={recordPayment}>Record payment</Button></>}>
        <div className="space-y-3">
          {payModal && (
            <p className="text-sm text-muted">
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
          <p className="text-2xs text-muted">
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
          <p className="text-2xs text-muted">
            A drafted lease changes nothing until you activate it. Activating marks the unit occupied.
          </p>
        </div>
      </Modal>
    </div>
  )
}

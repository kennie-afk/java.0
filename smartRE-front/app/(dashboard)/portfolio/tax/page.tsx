'use client'
import { useCallback, useEffect, useState } from 'react'
import { CalendarClock, Download, Percent, Receipt, AlertTriangle } from 'lucide-react'
import toast from 'react-hot-toast'
import { pmsApi, type MriSummary } from '@/lib/api'
import { useAuthGuard } from '@/hooks/useAuthGuard'
import { LANDLORD_ROLES } from '@/lib/roles'
import { Card, StatCard } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import { PageLoader } from '@/components/ui/Modal'
import { fmt } from '@/lib/utils'

const lastMonth = () => {
  const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

export default function RentalTaxPage() {
  const { ready } = useAuthGuard(LANDLORD_ROLES)
  const [month, setMonth] = useState(lastMonth())
  const [data, setData] = useState<MriSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [rate, setRate] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try { setData(await pmsApi.mri.summary(month)) }
    catch { toast.error('Could not load the rental income summary.') }
    finally { setLoading(false) }
  }, [month])

  useEffect(() => { if (ready) load() }, [ready, load])

  async function saveRate(value: number | null) {
    try {
      await pmsApi.mri.setRate(value)
      toast.success(value === null ? 'Back to the platform default rate.' : 'Your rate is saved.')
      setRate(''); load()
    } catch { toast.error('That rate was not accepted. Use a number from 0 to 100.') }
  }

  async function download() {
    try {
      const blob = await pmsApi.mri.exportCsv(month)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url; a.download = `rental-income-${month}.csv`; a.click()
      URL.revokeObjectURL(url)
    } catch { toast.error('Could not prepare the file.') }
  }

  if (!ready) return <PageLoader />

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-lg font-semibold">Rental income tax</h1>
        <p className="text-sm text-muted-foreground">
          Rent you actually received in a month, what the rate makes of it, and when it is due. A preparation aid: nothing is filed from here.
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="w-44"><Input label="Month" type="month" value={month} onChange={e => setMonth(e.target.value)} /></div>
        <Button variant="outline" onClick={download} disabled={!data || data.receipts === 0}>
          <Download size={14} /> Download CSV
        </Button>
      </div>

      {loading && <PageLoader />}
      {!loading && data && (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <StatCard label="Rent received" value={fmt.currency(data.grossRentReceived)} sub={`${data.receipts} receipts`} icon={<Receipt size={16} />} />
            <StatCard label={`Tax at ${data.ratePercent}%`} value={fmt.currency(data.taxEstimate)} sub={data.rateIsLandlordOverride ? 'your rate' : 'platform default, not verified for you'} icon={<Percent size={16} />} color="blue" />
            <StatCard label="Due" value={data.dueDate} sub={data.daysUntilDue >= 0 ? `${data.daysUntilDue} days left` : `${-data.daysUntilDue} days overdue`} icon={<CalendarClock size={16} />} color={data.daysUntilDue < 0 ? 'purple' : 'emerald'} />
            <StatCard label="Properties" value={data.byProperty.length} sub="with rent this month" />
          </div>

          {!data.annualisedWithinConfiguredBand && data.grossRentReceived > 0 && (
            <div className="flex items-start gap-2 text-sm text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10 rounded-lg p-3">
              <AlertTriangle size={15} className="mt-0.5 shrink-0" />
              <span>This month, annualised, falls outside the income band this scheme is configured for. Another method may apply: check with KRA or your accountant.</span>
            </div>
          )}

          <Card className="p-4 space-y-3">
            <h2 className="text-sm font-medium">Your rate</h2>
            <p className="text-xs text-muted-foreground">
              Published sources disagree on the rate, so the platform does not decide it for you. Enter the rate you have confirmed with KRA.
            </p>
            <div className="flex flex-wrap items-end gap-3">
              <div className="w-32"><Input label="Rate (%)" type="number" min={0} max={100} step="0.01" value={rate} onChange={e => setRate(e.target.value)} placeholder={String(data.ratePercent)} /></div>
              <Button onClick={() => saveRate(Number(rate))} disabled={rate === '' || Number.isNaN(Number(rate))}>Save rate</Button>
              {data.rateIsLandlordOverride && <Button variant="outline" onClick={() => saveRate(null)}>Use the default</Button>}
            </div>
          </Card>

          <Card className="p-4">
            <h2 className="text-sm font-medium mb-2">By property</h2>
            {data.byProperty.length === 0
              ? <p className="text-sm text-muted-foreground">No confirmed rent was received in {data.month}.</p>
              : <table className="w-full text-sm">
                  <thead><tr className="text-left text-xs text-muted-foreground"><th className="py-1">Property</th><th className="py-1 text-right">Receipts</th><th className="py-1 text-right">Rent received</th></tr></thead>
                  <tbody>{data.byProperty.map(p => (
                    <tr key={p.propertyId} className="border-t border-border"><td className="py-1.5 font-mono text-xs">{p.propertyId.slice(0, 8)}</td><td className="py-1.5 text-right">{p.receipts}</td><td className="py-1.5 text-right">{fmt.currency(p.gross)}</td></tr>
                  ))}</tbody>
                </table>}
          </Card>

          <p className="text-xs text-muted-foreground">{data.notice}</p>
        </>
      )}
    </div>
  )
}

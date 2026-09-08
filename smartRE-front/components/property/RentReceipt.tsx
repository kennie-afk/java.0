'use client'

import { useEffect, useState } from 'react'
import { Printer, Download } from 'lucide-react'
import { pmsApi } from '@/lib/api'
import type { InvoiceResponse, RentPaymentResponse } from '@/types'
import { Modal } from '@/components/ui/Modal'
import { LogoMark } from '@/components/brand/Logo'
import Button from '@/components/ui/Button'
import { fmt } from '@/lib/utils'
import { downloadDocument } from '@/lib/pdf'

/**
 * A receipt for rent already paid.
 *
 * <p>Two deliberate constraints. It only shows payments the system has actually
 * confirmed — a PENDING M-Pesa push is not money received, and a receipt that says it is
 * would be a document the landlord could be held to. And it is built entirely from what
 * the server returned, never from what the caller typed into the payment form, so it
 * cannot certify a figure that was never recorded.
 *
 * <p>The whole thing is printed via the browser rather than generated server-side as a
 * PDF: nothing here needs a signature or a stored artefact, and a printable page works
 * on a phone in a rental office without a print server.
 */
export default function RentReceipt({
  invoice,
  landlordName,
  propertyName,
  onClose,
}: {
  invoice: InvoiceResponse | null
  landlordName?: string
  propertyName?: string
  onClose: () => void
}) {
  const [payments, setPayments] = useState<RentPaymentResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!invoice) { setPayments(null); setError(null); return }
    let cancelled = false
    pmsApi.invoices.payments(invoice.id)
      .then(rows => { if (!cancelled) setPayments(rows) })
      .catch(() => { if (!cancelled) setError('Could not load the payments for this invoice.') })
    return () => { cancelled = true }
  }, [invoice])

  if (!invoice) return null

  const confirmed = (payments ?? []).filter(p => p.status === 'CONFIRMED')
  const receipted = confirmed.reduce((sum, p) => sum + p.amount, 0)
  const lastPaidAt = confirmed
    .map(p => p.paidAt ?? p.createdAt)
    .sort()
    .at(-1)

  /**
   * The same figures the modal shows, redrawn as a PDF. Built from the loaded payments
   * rather than scraped from the DOM, so the file cannot drift from what was verified on
   * screen and its text stays selectable.
   */
  const save = async () => {
    setSaving(true)
    try {
      await downloadDocument({
        title: 'Rent receipt',
        reference: invoice.invoiceNumber,
        issued: fmt.date(new Date().toISOString()),
        filename: `SmartRE-receipt-${invoice.invoiceNumber}`,
        fields: [
          { label: 'Received from', value: invoice.tenantName || 'Tenant' },
          { label: 'Unit', value: [invoice.unitLabel, propertyName].filter(Boolean).join(' · ') || '—' },
          { label: 'Period', value: `${fmt.date(invoice.periodStart)} — ${fmt.date(invoice.periodEnd)}` },
          ...(landlordName ? [{ label: 'Received by', value: landlordName }] : []),
        ],
        linesTitle: 'Payments confirmed',
        lines: confirmed.map(p => ({
          left: `${fmt.date(p.paidAt ?? p.createdAt)} · ${p.method.replace(/_/g, ' ').toLowerCase()}`
                + (p.mpesaReceipt ? ` · ${p.mpesaReceipt}` : ''),
          right: fmt.currency(p.amount),
        })),
        totals: [
          { label: 'Rent for the period', value: fmt.currency(invoice.amountDue) },
          { label: 'Received', value: fmt.currency(receipted), strong: true },
          ...(invoice.balance > 0
            ? [{ label: 'Still outstanding', value: fmt.currency(invoice.balance), alert: true }]
            : []),
        ],
        note: invoice.balance > 0
          ? `This receipts ${fmt.currency(receipted)} against ${invoice.invoiceNumber}. It is not a receipt for the full period.`
          : `${invoice.invoiceNumber} is settled in full.`,
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={!!invoice}
      onClose={onClose}
      title="Rent receipt"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Close</Button>
          <Button variant="secondary" onClick={save} loading={saving} disabled={!confirmed.length}>
            <Download size={13}/> Download
          </Button>
          <Button onClick={() => window.print()} disabled={!confirmed.length}>
            <Printer size={13}/> Print
          </Button>
        </>
      }
    >
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      {!error && payments === null && (
        <p className="text-sm text-muted">Loading payments…</p>
      )}

      {!error && payments !== null && !confirmed.length && (
        <p className="text-sm text-muted">
          Nothing has been confirmed against {invoice.invoiceNumber} yet, so there is no
          receipt to issue. A payment that is still pending is not money received.
        </p>
      )}

      {!!confirmed.length && (
        <div id="print-root" className="receipt space-y-4 text-gray-900 dark:text-white">
          <div className="flex items-start justify-between gap-4 border-b border-base pb-3">
            <div className="flex items-center gap-2.5 min-w-0">
              {/* The mark belongs here more than anywhere else in the app: this is the
                  one thing a tenant prints, keeps, and may later produce as evidence of
                  what they paid. At this size it draws the simplified flat gold, which
                  is also what survives a monochrome office printer. */}
              <LogoMark size={30} idSuffix="receipt"/>
              <div className="min-w-0">
                <p className="text-base font-semibold leading-tight">
                  <span>Smart</span><span className="text-gold-600">RE</span>
                  <span className="font-normal"> rent receipt</span>
                </p>
                <p className="text-2xs text-muted tabular-nums">{invoice.invoiceNumber}</p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-2xs text-muted">Issued</p>
              <p className="text-2xs tabular-nums">{fmt.date(new Date().toISOString())}</p>
            </div>
          </div>

          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            <div>
              <dt className="text-2xs text-muted">Received from</dt>
              <dd>{invoice.tenantName || 'Tenant'}</dd>
            </div>
            {/* Omitted rather than filled with "Landlord": a receipt that names nobody
                is honest, one that names a role pretends to identify a party. */}
            {landlordName && (
              <div>
                <dt className="text-2xs text-muted">Received by</dt>
                <dd>{landlordName}</dd>
              </div>
            )}
            <div>
              <dt className="text-2xs text-muted">Unit</dt>
              <dd>{[invoice.unitLabel, propertyName].filter(Boolean).join(' · ') || '—'}</dd>
            </div>
            <div>
              <dt className="text-2xs text-muted">Period</dt>
              <dd className="tabular-nums">
                {fmt.date(invoice.periodStart)} — {fmt.date(invoice.periodEnd)}
              </dd>
            </div>
          </dl>

          <div>
            <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">
              Payments confirmed
            </p>
            <ul className="divide-y divide-[color:var(--border)] border-y border-base">
              {confirmed.map(p => (
                <li key={p.id} className="py-2 flex items-baseline justify-between gap-3 text-sm">
                  <span className="min-w-0">
                    <span className="tabular-nums">{fmt.date(p.paidAt ?? p.createdAt)}</span>
                    <span className="text-muted"> · {p.method.replace(/_/g, ' ').toLowerCase()}</span>
                    {p.mpesaReceipt && (
                      <span className="text-muted"> · {p.mpesaReceipt}</span>
                    )}
                  </span>
                  <span className="font-medium tabular-nums shrink-0">{fmt.currency(p.amount)}</span>
                </li>
              ))}
            </ul>
          </div>

          <div className="space-y-1 text-sm tabular-nums">
            <div className="flex justify-between">
              <span className="text-muted">Rent for the period</span>
              <span>{fmt.currency(invoice.amountDue)}</span>
            </div>
            <div className="flex justify-between font-semibold">
              <span>Received</span>
              <span>{fmt.currency(receipted)}</span>
            </div>
            {invoice.balance > 0 && (
              <div className="flex justify-between text-red-600 dark:text-red-400">
                <span>Still outstanding</span>
                <span>{fmt.currency(invoice.balance)}</span>
              </div>
            )}
          </div>

          <p className="text-2xs text-muted border-t border-base pt-3">
            {invoice.balance > 0
              ? `This receipts ${fmt.currency(receipted)} against ${invoice.invoiceNumber}. It is not a receipt for the full period.`
              : `${invoice.invoiceNumber} is settled in full.`}
            {lastPaidAt && ` Last payment ${fmt.date(lastPaidAt)}.`}
          </p>
        </div>
      )}
    </Modal>
  )
}

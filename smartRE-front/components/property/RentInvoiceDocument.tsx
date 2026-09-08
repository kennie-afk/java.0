'use client'

import { useEffect, useState } from 'react'
import { Printer, Download } from 'lucide-react'
import { pmsApi } from '@/lib/api'
import type { InvoiceResponse, RentPaymentResponse } from '@/types'
import { Modal } from '@/components/ui/Modal'
import { LogoMark } from '@/components/brand/Logo'
import Button from '@/components/ui/Button'
import { downloadDocument } from '@/lib/pdf'
import { fmt } from '@/lib/utils'

/**
 * The invoice itself, as a document.
 *
 * <p>Rent invoices existed only as rows in a list. A receipt proves what was paid; an
 * invoice states what is owed, and a tenant asked to produce one — for an employer's
 * housing allowance, a bank, or their own records — had nothing to produce.
 *
 * <p>It shows payments already made against the invoice, so the outstanding figure can be
 * checked rather than taken on trust. Unlike the receipt it renders whatever the state:
 * an unpaid invoice is exactly the case someone needs a copy of.
 */
export default function RentInvoiceDocument({
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
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!invoice) { setPayments(null); return }
    let cancelled = false
    pmsApi.invoices.payments(invoice.id)
      .then(rows => { if (!cancelled) setPayments(rows) })
      // A failure here is not fatal: the invoice is still complete without its payment
      // history, and an empty list is honest about what could not be loaded.
      .catch(() => { if (!cancelled) setPayments([]) })
    return () => { cancelled = true }
  }, [invoice])

  if (!invoice) return null

  const confirmed = (payments ?? []).filter(p => p.status === 'CONFIRMED')
  const overdue = invoice.balance > 0 && new Date(invoice.dueDate) < new Date()

  const save = async () => {
    setSaving(true)
    try {
      await downloadDocument({
        title: 'Rent invoice',
        reference: invoice.invoiceNumber,
        issued: fmt.date(invoice.createdAt),
        filename: `SmartRE-invoice-${invoice.invoiceNumber}`,
        fields: [
          { label: 'Billed to', value: invoice.tenantName || 'Tenant' },
          { label: 'Unit', value: [invoice.unitLabel, propertyName].filter(Boolean).join(' · ') || '—' },
          { label: 'Period', value: `${fmt.date(invoice.periodStart)} — ${fmt.date(invoice.periodEnd)}` },
          { label: 'Due', value: fmt.date(invoice.dueDate) },
          ...(landlordName ? [{ label: 'Issued by', value: landlordName }] : []),
        ],
        linesTitle: confirmed.length ? 'Payments received' : undefined,
        lines: confirmed.map(p => ({
          left: `${fmt.date(p.paidAt ?? p.createdAt)} · ${p.method.replace(/_/g, ' ').toLowerCase()}`
                + (p.mpesaReceipt ? ` · ${p.mpesaReceipt}` : ''),
          right: fmt.currency(p.amount),
        })),
        totals: [
          { label: 'Rent for the period', value: fmt.currency(invoice.amountDue) },
          ...(invoice.amountPaid > 0
            ? [{ label: 'Paid', value: fmt.currency(invoice.amountPaid) }] : []),
          { label: invoice.balance > 0 ? 'Amount due' : 'Settled',
            value: fmt.currency(invoice.balance), strong: true, alert: overdue },
        ],
        note: invoice.balance > 0
          ? `Payable by ${fmt.date(invoice.dueDate)}.` + (overdue ? ' This invoice is past its due date.' : '')
          : 'This invoice is settled in full. No payment is due.',
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={!!invoice}
      onClose={onClose}
      title="Rent invoice"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Close</Button>
          <Button variant="secondary" onClick={save} loading={saving}>
            <Download size={13}/> Download
          </Button>
          <Button onClick={() => window.print()}>
            <Printer size={13}/> Print
          </Button>
        </>
      }
    >
      <div id="print-root" className="space-y-4 text-gray-900 dark:text-white">
        <div className="flex items-start justify-between gap-4 border-b border-base pb-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <LogoMark size={30} idSuffix="invoice"/>
            <div className="min-w-0">
              <p className="text-base font-semibold leading-tight">
                <span>Smart</span><span className="text-gold-600">RE</span>
                <span className="font-normal"> rent invoice</span>
              </p>
              <p className="text-2xs text-muted tabular-nums">{invoice.invoiceNumber}</p>
            </div>
          </div>
          <div className="text-right shrink-0">
            <p className="text-2xs text-muted">Issued</p>
            <p className="text-2xs tabular-nums">{fmt.date(invoice.createdAt)}</p>
          </div>
        </div>

        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <div>
            <dt className="text-2xs text-muted">Billed to</dt>
            <dd>{invoice.tenantName || 'Tenant'}</dd>
          </div>
          <div>
            <dt className="text-2xs text-muted">Unit</dt>
            <dd>{[invoice.unitLabel, propertyName].filter(Boolean).join(' · ') || '—'}</dd>
          </div>
          <div>
            <dt className="text-2xs text-muted">Period</dt>
            <dd className="tabular-nums">{fmt.date(invoice.periodStart)} — {fmt.date(invoice.periodEnd)}</dd>
          </div>
          <div>
            <dt className="text-2xs text-muted">Due</dt>
            <dd className={overdue ? 'text-red-600 dark:text-red-400 tabular-nums' : 'tabular-nums'}>
              {fmt.date(invoice.dueDate)}
            </dd>
          </div>
        </dl>

        {!!confirmed.length && (
          <div>
            <p className="text-2xs font-medium text-muted uppercase tracking-[0.06em] mb-1.5">
              Payments received
            </p>
            <ul className="divide-y divide-[color:var(--border)] border-y border-base">
              {confirmed.map(p => (
                <li key={p.id} className="py-2 flex items-baseline justify-between gap-3 text-sm">
                  <span className="min-w-0">
                    <span className="tabular-nums">{fmt.date(p.paidAt ?? p.createdAt)}</span>
                    <span className="text-muted"> · {p.method.replace(/_/g, ' ').toLowerCase()}</span>
                    {p.mpesaReceipt && <span className="text-muted"> · {p.mpesaReceipt}</span>}
                  </span>
                  <span className="font-medium tabular-nums shrink-0">{fmt.currency(p.amount)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="space-y-1 text-sm tabular-nums">
          <div className="flex justify-between">
            <span className="text-muted">Rent for the period</span>
            <span>{fmt.currency(invoice.amountDue)}</span>
          </div>
          {invoice.amountPaid > 0 && (
            <div className="flex justify-between">
              <span className="text-muted">Paid</span>
              <span>{fmt.currency(invoice.amountPaid)}</span>
            </div>
          )}
          <div className={`flex justify-between font-semibold ${overdue ? 'text-red-600 dark:text-red-400' : ''}`}>
            <span>{invoice.balance > 0 ? 'Amount due' : 'Settled'}</span>
            <span>{fmt.currency(invoice.balance)}</span>
          </div>
        </div>

        <p className="text-2xs text-muted border-t border-base pt-3">
          {invoice.balance > 0
            ? `Payable by ${fmt.date(invoice.dueDate)}.${overdue ? ' This invoice is past its due date.' : ''}`
            : 'This invoice is settled in full. No payment is due.'}
        </p>
      </div>
    </Modal>
  )
}

'use client'

import { useState } from 'react'

import { Printer, Download } from 'lucide-react'
import type { PaymentReceiptResponse, PaymentResponse } from '@/types'
import { Modal } from '@/components/ui/Modal'
import { LogoMark } from '@/components/brand/Logo'
import Button from '@/components/ui/Button'
import { fmt } from '@/lib/utils'
import { downloadDocument } from '@/lib/pdf'

/**
 * The receipt for a property payment.
 *
 * <p>The same transaction produces two different documents depending on who is reading
 * it, and that is deliberate rather than cosmetic. A buyer's receipt states what they
 * paid; it has no business showing them the platform's fee or what the seller cleared,
 * which is the seller's commercial information. A seller's receipt has to show the
 * deduction, because a payout that differs from the sale price without explanation is
 * how disputes start.
 *
 * <p>Only a completed payment produces one. A pending M-Pesa push is not money received,
 * and a receipt is a document someone may later rely on.
 */
export default function PaymentReceipt({
  payment,
  receipt,
  viewerId,
  propertyTitle,
  onClose,
}: {
  payment: PaymentResponse | null
  receipt: PaymentReceiptResponse | null
  viewerId?: string
  propertyTitle?: string
  onClose: () => void
}) {
  const [saving, setSaving] = useState(false)

  if (!payment) return null

  const isSeller = !!viewerId && viewerId === payment.sellerId
  const settled = payment.status === 'COMPLETED'

  /** The buyer's copy and the seller's copy differ, and so do their PDFs. */
  const save = async () => {
    setSaving(true)
    try {
      await downloadDocument({
        title: 'Payment receipt',
        reference: receipt?.receiptNumber ?? payment.id,
        issued: fmt.date(receipt?.issuedAt ?? payment.updatedAt ?? new Date().toISOString()),
        filename: `SmartRE-payment-${receipt?.receiptNumber ?? payment.id}`,
        fields: [
          { label: isSeller ? 'Received for' : 'Paid for', value: propertyTitle || 'Property' },
          { label: 'Type', value: payment.paymentType.replace(/_/g, ' ').toLowerCase() },
          ...(payment.mpesaReceiptNumber
            ? [{ label: 'M-Pesa receipt', value: payment.mpesaReceiptNumber }] : []),
          { label: 'Escrow', value: payment.escrowReleased ? 'Released' : 'Held pending completion' },
        ],
        totals: isSeller && receipt
          ? [
              { label: 'Buyer paid', value: fmt.currency(receipt.grossAmount) },
              { label: 'Platform fee', value: `−${fmt.currency(receipt.platformFee)}` },
              { label: 'Your payout', value: fmt.currency(receipt.sellerPayout), strong: true },
            ]
          : [
              { label: 'Amount paid', value: fmt.currency(receipt?.grossAmount ?? payment.amount), strong: true },
            ],
        note: (payment.escrowReleased
          ? 'Funds have been released.'
          : 'Funds are held in escrow and released on completion.')
          + ' This receipt records a payment processed through SmartRE.',
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={!!payment}
      onClose={onClose}
      title="Payment receipt"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Close</Button>
          <Button variant="secondary" onClick={save} loading={saving} disabled={!settled}>
            <Download size={13}/> Download
          </Button>
          <Button onClick={() => window.print()} disabled={!settled}>
            <Printer size={13}/> Print
          </Button>
        </>
      }
    >
      {!settled ? (
        <p className="text-sm text-muted">
          This payment is {payment.status.toLowerCase().replace(/_/g, ' ')}. A receipt is
          issued once the money has actually settled.
        </p>
      ) : (
        <div id="print-root" className="space-y-4 text-gray-900 dark:text-white">
          <div className="flex items-start justify-between gap-4 border-b border-base pb-3">
            <div className="flex items-center gap-2.5 min-w-0">
              <LogoMark size={30} idSuffix="payreceipt"/>
              <div className="min-w-0">
                <p className="text-base font-semibold leading-tight">
                  <span>Smart</span><span className="text-gold-600">RE</span>
                  <span className="font-normal"> payment receipt</span>
                </p>
                <p className="text-2xs text-muted tabular-nums">
                  {receipt?.receiptNumber ?? payment.id}
                </p>
              </div>
            </div>
            <div className="text-right shrink-0">
              <p className="text-2xs text-muted">Issued</p>
              <p className="text-2xs tabular-nums">
                {fmt.date(receipt?.issuedAt ?? payment.updatedAt ?? new Date().toISOString())}
              </p>
            </div>
          </div>

          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            <div>
              <dt className="text-2xs text-muted">{isSeller ? 'Received for' : 'Paid for'}</dt>
              <dd>{propertyTitle || 'Property'}</dd>
            </div>
            <div>
              <dt className="text-2xs text-muted">Type</dt>
              <dd>{payment.paymentType.replace(/_/g, ' ').toLowerCase()}</dd>
            </div>
            {payment.mpesaReceiptNumber && (
              <div>
                <dt className="text-2xs text-muted">M-Pesa receipt</dt>
                <dd className="tabular-nums">{payment.mpesaReceiptNumber}</dd>
              </div>
            )}
            <div>
              <dt className="text-2xs text-muted">Escrow</dt>
              <dd>{payment.escrowReleased ? 'Released' : 'Held pending completion'}</dd>
            </div>
          </dl>

          <div className="space-y-1 text-sm tabular-nums border-t border-base pt-3">
            {isSeller && receipt ? (
              <>
                {/* The seller sees the arithmetic, because a payout that differs from
                    the sale price without explanation is how disputes start. */}
                <div className="flex justify-between">
                  <span className="text-muted">Buyer paid</span>
                  <span>{fmt.currency(receipt.grossAmount)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted">
                    Platform fee
                    {receipt.grossAmount
                      ? ` (${((receipt.platformFee / receipt.grossAmount) * 100).toFixed(1)}%)`
                      : ''}
                  </span>
                  <span>−{fmt.currency(receipt.platformFee)}</span>
                </div>
                <div className="flex justify-between font-semibold border-t border-base pt-1 mt-1">
                  <span>Your payout</span>
                  <span>{fmt.currency(receipt.sellerPayout)}</span>
                </div>
              </>
            ) : (
              <div className="flex justify-between font-semibold">
                <span>Amount paid</span>
                <span>{fmt.currency(receipt?.grossAmount ?? payment.amount)}</span>
              </div>
            )}
          </div>

          <p className="text-2xs text-muted border-t border-base pt-3">
            {payment.escrowReleased
              ? 'Funds have been released.'
              : 'Funds are held in escrow and released on completion.'}
            {' '}This receipt records a payment processed through SmartRE.
          </p>
        </div>
      )}
    </Modal>
  )
}

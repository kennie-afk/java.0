/**
 * PDF generation for the documents people keep — receipts and invoices.
 *
 * <p>Printing already worked, but print is not a file. A tenant asked for proof of what
 * they paid needs something to attach to an email or hand to a bank, and "open the app
 * and press Ctrl+P" is not that.
 *
 * <p>jsPDF is imported dynamically by {@link downloadDocument}, so roughly 350KB of
 * library only loads for the person who actually clicks Download. It is worth being
 * deliberate about that: this bundle is served to every visitor browsing listings, and
 * most of them will never generate a document.
 *
 * <p>Text is drawn rather than rasterised from the DOM. html2canvas would have been less
 * code and would have produced a blurry image several times the size, with no selectable
 * text — a receipt whose figures cannot be copied or searched is a photograph of a
 * receipt, not a document.
 */

/** A4 in points, which is jsPDF's default unit. */
const PAGE_WIDTH = 595.28
const MARGIN = 48
const CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

const GOLD: [number, number, number] = [201, 162, 39]
const INK: [number, number, number] = [34, 32, 27]
const MUTED: [number, number, number] = [122, 114, 99]
const RULE: [number, number, number] = [231, 226, 212]

export interface DocumentField { label: string; value: string }
export interface DocumentLine { left: string; right: string }
export interface DocumentTotal { label: string; value: string; strong?: boolean; alert?: boolean }

export interface DocumentSpec {
  /** Shown large at the top, e.g. "Rent receipt". */
  title: string
  /** Invoice or receipt number, under the title. */
  reference: string
  issued: string
  fields: DocumentField[]
  linesTitle?: string
  lines?: DocumentLine[]
  totals: DocumentTotal[]
  note?: string
  /** Becomes the download filename, sanitised. */
  filename: string
}

/**
 * The mark, fetched at generation time and embedded.
 *
 * <p>Returns null rather than throwing if it cannot be loaded: a receipt without a logo
 * is still a valid receipt, and failing the whole download because an image 404'd would
 * be the wrong trade.
 */
async function loadLogo(): Promise<string | null> {
  try {
    const res = await fetch('/logo-email.png')
    if (!res.ok) return null
    const blob = await res.blob()
    return await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onloadend = () => resolve(reader.result as string)
      reader.onerror = reject
      reader.readAsDataURL(blob)
    })
  } catch {
    return null
  }
}

export async function downloadDocument(spec: DocumentSpec): Promise<void> {
  const { jsPDF } = await import('jspdf')
  const doc = new jsPDF({ unit: 'pt', format: 'a4' })
  const logo = await loadLogo()

  let y = MARGIN

  // --- Letterhead -----------------------------------------------------------
  if (logo) doc.addImage(logo, 'PNG', MARGIN, y - 4, 28, 28)

  doc.setFont('helvetica', 'bold').setFontSize(15)
  doc.setTextColor(...INK)
  doc.text('Smart', MARGIN + (logo ? 36 : 0), y + 14)
  const smartWidth = doc.getTextWidth('Smart')
  doc.setTextColor(...GOLD)
  doc.text('RE', MARGIN + (logo ? 36 : 0) + smartWidth, y + 14)

  doc.setFont('helvetica', 'normal').setFontSize(9).setTextColor(...MUTED)
  doc.text(spec.issued, PAGE_WIDTH - MARGIN, y + 6, { align: 'right' })
  doc.text('Issued', PAGE_WIDTH - MARGIN, y - 5, { align: 'right' })

  y += 34
  doc.setDrawColor(...RULE).setLineWidth(0.8)
  doc.line(MARGIN, y, PAGE_WIDTH - MARGIN, y)
  y += 26

  // --- Title ----------------------------------------------------------------
  doc.setFont('helvetica', 'bold').setFontSize(17).setTextColor(...INK)
  doc.text(spec.title, MARGIN, y)
  y += 15
  doc.setFont('helvetica', 'normal').setFontSize(9).setTextColor(...MUTED)
  doc.text(spec.reference, MARGIN, y)
  y += 26

  // --- Fields, two per row --------------------------------------------------
  const colWidth = CONTENT_WIDTH / 2
  spec.fields.forEach((f, i) => {
    const col = i % 2
    if (col === 0 && i > 0) y += 30
    const x = MARGIN + col * colWidth
    doc.setFontSize(8).setTextColor(...MUTED)
    doc.text(f.label.toUpperCase(), x, y)
    doc.setFontSize(10).setTextColor(...INK)
    doc.text(f.value || '—', x, y + 12)
  })
  if (spec.fields.length) y += 34

  // --- Line items -----------------------------------------------------------
  if (spec.lines?.length) {
    doc.setFontSize(8).setTextColor(...MUTED)
    doc.text((spec.linesTitle ?? 'Detail').toUpperCase(), MARGIN, y)
    y += 12
    doc.setDrawColor(...RULE).line(MARGIN, y, PAGE_WIDTH - MARGIN, y)
    y += 15
    for (const line of spec.lines) {
      doc.setFontSize(9.5).setTextColor(...INK)
      doc.text(line.left, MARGIN, y)
      doc.text(line.right, PAGE_WIDTH - MARGIN, y, { align: 'right' })
      y += 9
      doc.setDrawColor(...RULE).line(MARGIN, y, PAGE_WIDTH - MARGIN, y)
      y += 15
    }
    y += 6
  }

  // --- Totals ---------------------------------------------------------------
  for (const t of spec.totals) {
    doc.setFont('helvetica', t.strong ? 'bold' : 'normal').setFontSize(10)
    doc.setTextColor(...(t.alert ? [180, 35, 31] as [number, number, number] : t.strong ? INK : MUTED))
    doc.text(t.label, MARGIN, y)
    doc.setTextColor(...(t.alert ? [180, 35, 31] as [number, number, number] : INK))
    doc.text(t.value, PAGE_WIDTH - MARGIN, y, { align: 'right' })
    y += 17
  }

  // --- Note -----------------------------------------------------------------
  if (spec.note) {
    y += 10
    doc.setDrawColor(...RULE).line(MARGIN, y, PAGE_WIDTH - MARGIN, y)
    y += 16
    doc.setFont('helvetica', 'normal').setFontSize(8.5).setTextColor(...MUTED)
    for (const line of doc.splitTextToSize(spec.note, CONTENT_WIDTH)) {
      doc.text(line, MARGIN, y)
      y += 11
    }
  }

  doc.save(safeFilename(spec.filename))
}

/** Reference numbers carry slashes and spaces; a filename should carry neither. */
function safeFilename(name: string): string {
  const cleaned = name.replace(/[^a-zA-Z0-9-_]+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '')
  return `${cleaned || 'smartre-document'}.pdf`
}

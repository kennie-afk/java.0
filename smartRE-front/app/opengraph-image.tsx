import { ImageResponse } from 'next/og'

export const runtime = 'edge'
export const alt = 'SmartRE Kenya: Identity-Verified Property Marketplace'
export const size = { width: 1200, height: 630 }
export const contentType = 'image/png'

export default async function Image() {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'linear-gradient(135deg, #FCFAF2 0%, #F0E3BE 50%, #E6D08F 100%)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 20, marginBottom: 28 }}>
          {/* Satori renders a subset of SVG and no <use>, gradients or filters, so the
              mark is redrawn here as plain paths in a single gold rather than imported.
              It is the same geometry as public/logo-icon.svg — if that changes, this
              must change with it. */}
          <svg width="104" height="104" viewBox="0 0 512 512">
            <path d="M 96 372 A 196 196 0 1 1 404 372"
                  fill="none" stroke="#C9A227" strokeWidth="22" strokeLinecap="round"/>
            <path d="M 46 206 H 84 L 106 228 H 150" fill="none" stroke="#AD8620" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M 46 256 H 150" fill="none" stroke="#C9A227" strokeWidth="8" strokeLinecap="round"/>
            <path d="M 46 306 H 84 L 106 284 H 150" fill="none" stroke="#AD8620" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M 268 174 L 164 174 L 164 246 L 258 246 L 258 318 L 154 318"
                  fill="none" stroke="#C9A227" strokeWidth="34" strokeLinejoin="miter"/>
            <path d="M 296 157 H 392 L 416 181 V 223 L 394 245 L 420 335 H 380 L 357 251 H 330 V 335 H 296 Z M 330 191 H 382 V 217 H 330 Z"
                  fill="#C9A227" fillRule="evenodd"/>
          </svg>
          <div style={{ fontSize: 64, fontWeight: 700, color: '#3A2F1F', display: 'flex' }}>
            <span>Smart</span><span style={{ color: '#C9A227' }}>RE</span>
          </div>
        </div>
        <div style={{ fontSize: 30, color: '#6B5114', maxWidth: 820, textAlign: 'center' }}>
          Identity-Verified Property Marketplace for Kenya
        </div>
        <div style={{ display: 'flex', gap: 32, marginTop: 36, fontSize: 20, color: '#8C6B1A' }}>
          <div>National ID + KRA verified</div>
          <div>·</div>
          <div>Ardhisasa title checks</div>
          <div>·</div>
          <div>M-Pesa escrow</div>
        </div>
      </div>
    ),
    { ...size }
  )
}

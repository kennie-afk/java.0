import { cn } from '@/lib/utils'

/**
 * Below this width the detail in the full mark stops being detail and becomes dirt: the
 * three circuit traces merge into one grey smear, the specular highlight reads as a
 * blown-out patch, and the contact shadow just softens the silhouette.
 *
 * 56 rather than something smaller because it was checked on screen, not guessed. At
 * 40px the traces were still illegible and the metal read as a stain. In practice this
 * means every placement in the app chrome — header, sidebar, footer, sign-in — draws the
 * simplified mark, and the full metallic one is reserved for places it is actually shown
 * at size: the OG image, a README, a slide.
 */
const SIMPLIFY_BELOW_PX = 56

/**
 * The SmartRE mark.
 *
 * <p>Inlined rather than loaded from /logo-icon.svg so it renders in the first paint
 * alongside the rest of the shell — a header logo that arrives one network round trip
 * late is the most visible loading artefact a page has.
 *
 * <p>Gradient ids are suffixed per instance. Two copies of this component on one page
 * (header and footer, say) would otherwise declare the same ids twice, and the second
 * set would silently win for both.
 */
export function LogoMark({ size = 32, className, idSuffix = 'a' }: {
  size?: number
  className?: string
  idSuffix?: string
}) {
  const id = (name: string) => `sre-${name}-${idSuffix}`

  if (size < SIMPLIFY_BELOW_PX) {
    // The same geometry, flattened to one gold and stripped to the two things that still
    // carry identity this small: the open ring and the monogram.
    return (
      <svg
        viewBox="0 0 512 512"
        width={size}
        height={size}
        className={cn('shrink-0', className)}
        role="img"
        aria-label="SmartRE"
      >
        <path d="M 96 372 A 196 196 0 1 1 404 372"
              fill="none" stroke="#C9A227" strokeWidth="30" strokeLinecap="round"/>
        <g transform="translate(268 262) scale(0.92) translate(-287 -240)">
          <path d="M 268 168 L 164 168 L 164 240 L 258 240 L 258 312 L 154 312"
                fill="none" stroke="#C9A227" strokeWidth="38"
                strokeLinecap="butt" strokeLinejoin="miter"/>
          <path d="M 296 151 H 392 L 416 175 V 217 L 394 239 L 420 329 H 380 L 357 245 H 330 V 329 H 296 Z M 330 185 H 382 V 211 H 330 Z"
                fill="#C9A227" fillRule="evenodd"/>
        </g>
      </svg>
    )
  }

  return (
    <svg
      viewBox="0 0 512 512"
      width={size}
      height={size}
      className={cn('shrink-0', className)}
      role="img"
      aria-label="SmartRE"
    >
      <defs>
        {/* Gold is not one colour: specular, midtone, shadow, then a second lift where
            light bounces back. Flat gold reads as mustard. */}
        <linearGradient id={id('gold')} x1="0.05" y1="0" x2="0.95" y2="1">
          <stop offset="0%" stopColor="#FBEFC0"/>
          <stop offset="16%" stopColor="#E8CE7A"/>
          <stop offset="40%" stopColor="#C9A227"/>
          <stop offset="64%" stopColor="#9C7A1C"/>
          <stop offset="85%" stopColor="#C9A227"/>
          <stop offset="100%" stopColor="#7E6116"/>
        </linearGradient>
        <linearGradient id={id('extrude')} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#7E6116"/>
          <stop offset="55%" stopColor="#584410"/>
          <stop offset="100%" stopColor="#8C6B1A"/>
        </linearGradient>
        <linearGradient id={id('spec')} x1="0" y1="0" x2="0.72" y2="1">
          <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0.80"/>
          <stop offset="20%" stopColor="#FFFFFF" stopOpacity="0.26"/>
          <stop offset="46%" stopColor="#FFFFFF" stopOpacity="0"/>
        </linearGradient>
        <linearGradient id={id('arc')} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#EBD48A"/>
          <stop offset="32%" stopColor="#D9BC66"/>
          <stop offset="62%" stopColor="#C9A227"/>
          <stop offset="100%" stopColor="#8C6B1A"/>
        </linearGradient>

        <g id={id('letters')}>
          <path
            d="M 268 168 L 164 168 L 164 240 L 258 240 L 258 312 L 154 312"
            fill="none" strokeWidth="34" strokeLinecap="butt" strokeLinejoin="miter"
          />
          <path
            d="M 296 151 H 392 L 416 175 V 217 L 394 239 L 420 329 H 380 L 357 245 H 330 V 329 H 296 Z M 330 185 H 382 V 211 H 330 Z"
            fillRule="evenodd"
          />
        </g>
        <clipPath id={id('clip')}>
          <use href={`#${id('letters')}`} stroke="#000" strokeWidth="34"
               strokeLinecap="butt" strokeLinejoin="miter"/>
        </clipPath>
      </defs>

      {/* Ring: one long arc carrying most of the circle, plus a hairline of the
          specular colour inset on the lit side so the band reads as rounded rather than
          flat, and a thin outer arc on the left. */}
      <g fill="none" strokeLinecap="round">
        <path d="M 96 372 A 196 196 0 1 1 404 372" stroke={`url(#${id('arc')})`} strokeWidth="20"/>
        <path d="M 96 372 A 196 196 0 1 1 404 372" stroke="#FBEFC0" strokeWidth="4.5"
              opacity="0.38" transform="translate(-2.5 -2.5)"/>
        <path d="M 62 330 A 214 214 0 0 1 62 182" stroke="#D9BC66" strokeWidth="7" opacity="0.85"/>
      </g>

      <g fill="none" strokeLinecap="round" strokeLinejoin="round" strokeWidth="7">
        <g stroke="#AD8620">
          <path d="M 46 206 H 84 L 106 228 H 150"/>
          <path d="M 46 306 H 84 L 106 284 H 150"/>
        </g>
        <path d="M 46 256 H 150" stroke="#C9A227"/>
        {/* The node centres are painted, not transparent, so a trace never shows through
            its own terminal on a coloured background. */}
        <g fill="#FFFFFF" stroke="#AD8620" strokeWidth="6">
          <circle cx="32" cy="206" r="10"/>
          <circle cx="32" cy="306" r="10"/>
        </g>
        <circle cx="32" cy="256" r="10" fill="#FFFFFF" stroke="#C9A227" strokeWidth="6"/>
      </g>

      <g transform="translate(0 6)">
        <use href={`#${id('letters')}`} transform="translate(7 7)"
             fill={`url(#${id('extrude')})`} stroke={`url(#${id('extrude')})`}/>
        <use href={`#${id('letters')}`}
             fill={`url(#${id('gold')})`} stroke={`url(#${id('gold')})`}/>
        <g clipPath={`url(#${id('clip')})`}>
          <rect x="130" y="130" width="310" height="220" fill={`url(#${id('spec')})`}/>
        </g>
      </g>
    </svg>
  )
}

/**
 * Mark plus wordmark.
 *
 * <p>The wordmark is real text, not paths: it stays crisp at every size, it is
 * selectable and readable to a screen reader, and it follows the theme without a second
 * asset. It splits the way TechMara's does — neutral first half, brand colour second —
 * because SmartRE is a TechMara product and the two logotypes should look like one
 * system. "RE" carries the gold because that is the half that names the domain.
 */
export default function Logo({ size = 32, showWordmark = true, className, idSuffix = 'a' }: {
  size?: number
  showWordmark?: boolean
  className?: string
  idSuffix?: string
}) {
  return (
    <span className={cn('flex items-center gap-2.5 min-w-0', className)}>
      <LogoMark size={size} idSuffix={idSuffix}/>
      {showWordmark && (
        <span className="font-display font-bold text-lg truncate leading-none">
          <span className="text-gray-900 dark:text-white">Smart</span>
          <span className="text-gold-500">RE</span>
        </span>
      )}
    </span>
  )
}

export function MilkingScene({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 420 300" className={className} role="img"
      aria-label="A farmer milking a cow at first light">
      <defs>
        <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#fbf0df" />
          <stop offset="100%" stopColor="#faf8f3" />
        </linearGradient>
        <linearGradient id="ground" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#e4efe7" />
          <stop offset="100%" stopColor="#d5e7da" />
        </linearGradient>
      </defs>

      <rect width="420" height="300" rx="18" fill="url(#sky)" />
      <circle cx="330" cy="74" r="30" fill="#a8620a" opacity="0.16" />
      <circle cx="330" cy="74" r="18" fill="#a8620a" opacity="0.28" />

      <path d="M0 214c46-14 78-8 120 4s84 14 132 2 106-16 168 4v76H0z" fill="url(#ground)" />
      <path d="M0 232c58-10 96-2 150 8s96 8 150-4 82-10 120-2v66H0z" fill="#1f6f4a" opacity="0.10" />

      <g opacity="0.30" fill="#1f6f4a">
        <path d="M44 206c0-16 8-28 8-28s8 12 8 28a8 8 0 0 1-16 0z" />
        <path d="M382 210c0-14 7-24 7-24s7 10 7 24a7 7 0 0 1-14 0z" />
      </g>

      <g>
        <ellipse cx="236" cy="222" rx="74" ry="10" fill="#1f6f4a" opacity="0.14" />
        <path d="M186 150h96a24 24 0 0 1 24 24v34a10 10 0 0 1-10 10h-6l-6-26-6 26h-14l-5-24-5 24h-56a24 24 0 0 1-24-24v-20a24 24 0 0 1 24-24z"
              fill="#3c3a35" />
        <path d="M214 150h50a24 24 0 0 1 24 24v18h-46a28 28 0 0 1-28-28z" fill="#faf8f3" opacity="0.92" />
        <path d="M192 196c10 6 22 8 34 6" stroke="#faf8f3" strokeWidth="4"
              strokeLinecap="round" opacity="0.5" fill="none" />
        <path d="M296 152c10-14 26-18 34-12 8 6 4 20-6 28-8 6-20 6-26 0z" fill="#3c3a35" />
        <circle cx="316" cy="160" r="3" fill="#faf8f3" />
        <path d="M300 140c-8-10-4-20 4-20s12 8 10 16" stroke="#3c3a35" strokeWidth="5"
              strokeLinecap="round" fill="none" />
        <path d="M182 178c-12 4-18 16-14 26" stroke="#3c3a35" strokeWidth="6"
              strokeLinecap="round" fill="none" />
        <g fill="#3c3a35">
          <rect x="196" y="208" width="9" height="26" rx="4" />
          <rect x="224" y="208" width="9" height="26" rx="4" />
          <rect x="266" y="208" width="9" height="26" rx="4" />
          <rect x="288" y="208" width="9" height="26" rx="4" />
        </g>
        <path d="M240 200c0 8 3 14 3 14" stroke="#f7e7c8" strokeWidth="5" strokeLinecap="round" />
      </g>

      <g>
        <rect x="118" y="206" width="26" height="14" rx="4" fill="#7a6a52" />
        <circle cx="150" cy="176" r="15" fill="#8a5a34" />
        <path d="M136 168c2-9 10-14 18-12 7 2 11 8 10 14-6-4-20-6-28-2z" fill="#2c2620" />
        <path d="M138 190h26a14 14 0 0 1 14 14v10h-52v-8a16 16 0 0 1 12-16z" fill="#1f6f4a" />
        <path d="M172 200c10 2 16 8 18 14" stroke="#8a5a34" strokeWidth="8"
              strokeLinecap="round" fill="none" />
        <path d="M170 208c8 0 14 4 18 8" stroke="#8a5a34" strokeWidth="8"
              strokeLinecap="round" fill="none" />
        <path d="M132 214h44v10a6 6 0 0 1-6 6h-32a6 6 0 0 1-6-6z" fill="#2f5f8a" opacity="0.9" />
      </g>

      <g>
        <path d="M196 226h34l-4 26a6 6 0 0 1-6 5h-14a6 6 0 0 1-6-5z" fill="#c9ccc4" />
        <path d="M196 226h34l-1 8h-32z" fill="#e4e7df" />
        <path d="M199 240h28l-2 12a5 5 0 0 1-5 4h-14a5 5 0 0 1-5-4z" fill="#ffffff" opacity="0.85" />
      </g>
    </svg>
  );
}

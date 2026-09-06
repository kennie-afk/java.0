export function Logo({ className = "h-7 w-7" }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" fill="none" className={className} aria-hidden="true">
      <path
        d="M24 4c0 0-11 12.4-11 20.2A11 11 0 0 0 24 35.4a11 11 0 0 0 11-11.2C35 16.4 24 4 24 4z"
        fill="var(--color-accent)"
      />
      <path d="M24 12.5c0 0-5.6 6.7-5.6 11a5.6 5.6 0 0 0 5.6 5.7z" fill="#ffffff" opacity="0.28" />
      <path
        d="M13.6 38.2c3.9-3.4 7.3-4.6 10.4-4.6s6.5 1.2 10.4 4.6"
        stroke="var(--color-amber)"
        strokeWidth="3.2"
        strokeLinecap="round"
      />
      <circle cx="24" cy="43.2" r="2.4" fill="var(--color-amber)" />
    </svg>
  );
}

export function Wordmark({ className = "" }: { className?: string }) {
  return (
    <span className={`flex items-center gap-2.5 ${className}`}>
      <Logo className="h-7 w-7" />
      <span className="text-[0.9375rem] font-semibold tracking-[-0.01em]">Soko</span>
    </span>
  );
}

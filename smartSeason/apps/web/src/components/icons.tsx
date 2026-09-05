import type { ReactNode } from "react";

const base = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.6,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const
};

export type IconName = "home" | "farms" | "seasons" | "workforce" | "fraud" | "marketplace";

export function Icon({ name, className }: { name: IconName; className?: string }) {
  const paths: Record<IconName, ReactNode> = {
    home: (
      <>
        <path d="M4 10.5 12 4l8 6.5" />
        <path d="M6 10v9h12v-9" />
      </>
    ),
    farms: (
      <>
        <path d="M12 20v-7" />
        <path d="M12 13c-3.5 0-5-2.2-5-5 3.5 0 5 2.2 5 5z" />
        <path d="M12 13c3.5 0 5-2.2 5-5-3.5 0-5 2.2-5 5z" />
        <path d="M5 20h14" />
      </>
    ),
    seasons: (
      <>
        <rect x="3.5" y="5" width="17" height="15" rx="2" />
        <path d="M3.5 10h17M8 3.5v3M16 3.5v3" />
      </>
    ),
    workforce: (
      <>
        <circle cx="9" cy="8" r="3" />
        <path d="M3.5 19a5.5 5.5 0 0 1 11 0" />
        <path d="M16 6.2a3 3 0 0 1 0 5.6M17.5 19a5.4 5.4 0 0 0-2-4.2" />
      </>
    ),
    fraud: (
      <>
        <path d="M12 3.5 5 6.5v5c0 4.3 2.9 7.9 7 9 4.1-1.1 7-4.7 7-9v-5z" />
        <path d="M12 9v3.5M12 15.5v.5" />
      </>
    ),
    marketplace: (
      <>
        <path d="M4 8h16l-1 4.5a2 2 0 0 1-2 1.6H7a2 2 0 0 1-2-1.6z" />
        <path d="M4 8 6 4h12l2 4" />
        <circle cx="9" cy="19" r="1.4" />
        <circle cx="16" cy="19" r="1.4" />
      </>
    )
  };

  return (
    <svg {...base} className={className} aria-hidden="true">
      {paths[name]}
    </svg>
  );
}

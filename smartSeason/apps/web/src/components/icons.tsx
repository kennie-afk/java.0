import type { ReactNode } from "react";

const base = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.6,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const
};

export type IconName =
  | "home"
  | "farms"
  | "seasons"
  | "workforce"
  | "fraud"
  | "marketplace"
  | "devices"
  | "money"
  | "platform"
  | "identity";

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
    ),
    devices: (
      <>
        <rect x="4" y="4" width="16" height="16" rx="2.5" />
        <rect x="9" y="9" width="6" height="6" rx="1" />
        <path d="M9 2.5v1.5M15 2.5v1.5M9 20v1.5M15 20v1.5M2.5 9H4M2.5 15H4M20 9h1.5M20 15h1.5" />
      </>
    ),
    money: (
      <>
        <rect x="2.5" y="6" width="19" height="12" rx="2" />
        <circle cx="12" cy="12" r="2.5" />
        <path d="M6 10v4M18 10v4" />
      </>
    ),
    platform: (
      <>
        <path d="M4 7.5 12 3.5l8 4-8 4z" />
        <path d="M4 12.5 12 16.5l8-4" />
        <path d="M4 17 12 21l8-4" />
      </>
    ),
    identity: (
      <>
        <circle cx="12" cy="8.5" r="3.5" />
        <path d="M5 20a7 7 0 0 1 14 0" />
      </>
    )
  };

  return (
    <svg {...base} className={className} aria-hidden="true">
      {paths[name]}
    </svg>
  );
}

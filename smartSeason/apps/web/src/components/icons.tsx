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
  | "identity"
  | "pulse"
  | "sparkle"
  | "menu"
  | "close"
  | "chevron"
  | "logout"
  | "tasks"
  | "alert"
  | "arrow"
  | "sun"
  | "moon"
  | "collapse"
  | "expand"
  | "user"
  | "leaf"
  | "cloud"
  | "bolt"
  | "clock"
  | "grid"
  | "tag"
  | "receipt"
  | "box"
  | "truck"
  | "card"
  | "book"
  | "send"
  | "link"
  | "bell"
  | "image"
  | "search"
  | "chart"
  | "scroll"
  | "list"
  | "chat";

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
    ),
    leaf: (
      <><path d="M5 19c0-8 5-13 14-14 0 9-5 14-13 14" /><path d="M5 19c3-4 6-6 9-8" /></>
    ),
    cloud: <path d="M7 18a4 4 0 0 1-.6-7.95A5.5 5.5 0 0 1 17 9.5a4.2 4.2 0 0 1 0 8.5z" />,
    bolt: <path d="M13 3.5 6 13h5l-1 7.5L18 11h-5z" />,
    clock: (
      <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>
    ),
    grid: (
      <><rect x="4" y="4" width="6.5" height="6.5" rx="1.2" /><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.2" /><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.2" /><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.2" /></>
    ),
    tag: (
      <><path d="M3.5 12.5V4.5h8l9 9-8 8z" /><path d="M8 8.5h.01" /></>
    ),
    receipt: (
      <><path d="M6 3.5h12v17l-2.2-1.5L13.6 20.5 12 19l-1.6 1.5L8.2 19 6 20.5z" /><path d="M9 8h6M9 12h6" /></>
    ),
    box: (
      <><path d="M4 7.5 12 3.5l8 4v9l-8 4-8-4z" /><path d="M4 7.5 12 11.5l8-4M12 11.5v9" /></>
    ),
    truck: (
      <><path d="M3 6.5h11v10H3zM14 10h4l3 3v3.5h-7" /><circle cx="7" cy="18" r="1.7" /><circle cx="17" cy="18" r="1.7" /></>
    ),
    card: (
      <><rect x="3" y="5.5" width="18" height="13" rx="2" /><path d="M3 10h18M7 15h4" /></>
    ),
    book: (
      <><path d="M5 4.5h11a3 3 0 0 1 3 3v12H8a3 3 0 0 1-3-3z" /><path d="M5 16.5a3 3 0 0 1 3-3h11" /></>
    ),
    send: <path d="M20.5 3.5 10 14M20.5 3.5 14 20.5l-4-6.5-6.5-4z" />,
    link: (
      <><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" /><path d="M14 10a4 4 0 0 0-5.7 0l-3 3A4 4 0 0 0 11 18.700l1-1" /></>
    ),
    bell: (
      <><path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z" /><path d="M10 20.5h4" /></>
    ),
    image: (
      <><rect x="3.5" y="5" width="17" height="14" rx="2" /><circle cx="9" cy="10" r="1.5" /><path d="m4 17 5-4.5 4 3.5 3-2.5 4 3.5" /></>
    ),
    search: (
      <><circle cx="11" cy="11" r="6.5" /><path d="m16 16 4.5 4.5" /></>
    ),
    chart: (
      <><path d="M4 20V5M4 20h16" /><path d="M8 16v-4M12.5 16V8M17 16v-6" /></>
    ),
    scroll: (
      <><path d="M7 4.5h11v12a3 3 0 0 1-3 3H6.500" /><path d="M7 4.500v12a3 3 0 0 1-3 3" /><path d="M10.500 9h4.500M10.500 13h4.500" /></>
    ),
    list: (
      <><path d="M8.500 7h11M8.500 12h11M8.500 17h11" /><path d="M4.500 7h.5M4.500 12h.5M4.500 17h.5" /></>
    ),
    chat: <path d="M4 5.5h16v10H12l-4.5 3.5v-3.5H4z" />,
    pulse: <path d="M3 12h4l2.5-6 4 12 2.5-6H21" />,
    sparkle: (
      <>
        <path d="M12 3.5 13.8 9l5.7 1.8-5.7 1.8L12 18l-1.8-5.4L4.5 10.8 10.2 9z" />
        <path d="M18.5 3v3M17 4.5h3" />
      </>
    ),
    menu: <path d="M4 7h16M4 12h16M4 17h16" />,
    close: <path d="M6 6l12 12M18 6 6 18" />,
    chevron: <path d="m7 10 5 5 5-5" />,
    logout: (
      <>
        <path d="M15 17l5-5-5-5" />
        <path d="M20 12H9" />
        <path d="M12 20H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h6" />
      </>
    ),
    tasks: (
      <>
        <rect x="4" y="4" width="16" height="16" rx="2.5" />
        <path d="m8.5 12 2.5 2.5 4.5-5" />
      </>
    ),
    alert: (
      <>
        <path d="M12 4 3 19.5h18z" />
        <path d="M12 10v4.5M12 17v.3" />
      </>
    ),
    arrow: <path d="M5 12h14M13 6l6 6-6 6" />,
    sun: (
      <>
        <circle cx="12" cy="12" r="4" />
        <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4" />
      </>
    ),
    moon: <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />,
    collapse: <path d="m11 7-5 5 5 5M18 7l-5 5 5 5" />,
    expand: <path d="m6 7 5 5-5 5M13 7l5 5-5 5" />,
    user: (
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

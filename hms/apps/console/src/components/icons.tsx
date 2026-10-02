import type { ReactNode } from "react";

const base = { viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 1.6, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };

const PATHS = {
  home: <><path d="M4 10.5 12 4l8 6.5" /><path d="M6 10v9h12v-9" /></>,
  patients: <><circle cx="9" cy="8" r="3" /><path d="M3.5 19a5.5 5.5 0 0 1 11 0" /><path d="M16 6.2a3 3 0 0 1 0 5.6M17.5 19a5.4 5.4 0 0 0-2-4.2" /></>,
  queue: <><path d="M5 7h14M5 12h14M5 17h9" /></>,
  calendar: <><rect x="3.5" y="5" width="17" height="15" rx="2" /><path d="M3.5 10h17M8 3.5v3M16 3.5v3" /></>,
  bed: <><path d="M3.5 18V7M3.5 14h17v4M20.5 14v-2.5A2.5 2.5 0 0 0 18 9h-7v5" /><circle cx="7.5" cy="11" r="1.8" /></>,
  flask: <><path d="M9.5 3.5h5M10.5 3.5v5.2L5 18a1.6 1.6 0 0 0 1.4 2.5h11.2A1.6 1.6 0 0 0 19 18l-5.5-9.3V3.5" /><path d="M7.7 14.5h8.6" /></>,
  pill: <><rect x="3.5" y="9" width="17" height="6" rx="3" transform="rotate(-35 12 12)" /><path d="m9.4 9.4 5.2 5.2" /></>,
  receipt: <><path d="M6 3.5h12v17l-3-2-3 2-3-2-3 2z" /><path d="M9 8.5h6M9 12h6" /></>,
  shield: <><path d="M12 3.5 5 6.5v5c0 4.3 2.9 7.9 7 9 4.1-1.1 7-4.7 7-9v-5z" /><path d="m9 12 2.2 2.2L15.2 10" /></>,
  chart: <><path d="M4 20V4M4 20h16" /><path d="M8 16v-4M12 16V8M16 16v-6" /></>,
  staff: <><rect x="3.5" y="6" width="17" height="13" rx="2" /><path d="M9 6V4.5h6V6M3.5 12h17" /></>,
  key: <><circle cx="8" cy="12" r="3.5" /><path d="M11.5 12H20M17 12v3M14.5 12v2" /></>,
  building: <><path d="M5 20V5.5h9V20M14 10h5V20M3.5 20h17" /><path d="M8 9h3M8 12.5h3M8 16h3" /></>,
  audit: <><path d="M7 3.5h8l3.5 3.5v13.5H7z" /><path d="M10 12h5M10 15.5h5M10 8.5h2" /></>,
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  chevron: <path d="m7 10 5 5 5-5" />,
  logout: <><path d="M15 17l5-5-5-5" /><path d="M20 12H9" /><path d="M12 20H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h6" /></>,
  alert: <><path d="M12 4 3 19.5h18z" /><path d="M12 10v4.5M12 17v.3" /></>,
  clock: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>,
  money: <><rect x="2.5" y="6" width="19" height="12" rx="2" /><circle cx="12" cy="12" r="2.5" /></>,
  pulse: <path d="M3 12h4l2.5-6 4 12 2.5-6H21" />,
  search: <><circle cx="11" cy="11" r="6.5" /><path d="m16 16 4.5 4.5" /></>,
  inbox: <><path d="M4 13.5 6.5 5h11L20 13.5V19H4z" /><path d="M4 13.5h4.5a3.5 3.5 0 0 0 7 0H20" /></>
};

export type IconName = keyof typeof PATHS;

export function Icon({ name, className }: { name: IconName; className?: string }) {
  return (
    <svg {...base} className={className} aria-hidden="true">
      {PATHS[name] as ReactNode}
    </svg>
  );
}

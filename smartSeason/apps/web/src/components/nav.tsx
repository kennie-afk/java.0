"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { Icon, type IconName } from "@/components/icons";
import { SignOutButton } from "@/components/sign-out-button";
import { GROUPS } from "@/lib/catalogue.generated";
import {
  canUse,
  canSeeAdvisor,
  canSeeGroup,
  canSeeDairy,
  canSeeLiveBoard,
  canSeeMyWork,
  canSeeService,
  primaryRole,
  ROLE_LABELS
} from "@/lib/roles";

/**
 * The application shell, laid out exactly as SmartRE's: a fixed 260px sidebar (76px rail
 * when collapsed), a fixed 64px top bar, and a main column with 12/16px padding.
 *
 * What appears in the menu is decided by the same generated catalogue and role matrix the
 * controllers are annotated from, so the menu cannot offer what the service refuses.
 */
const SIDEBAR_WIDTH = 260;
const SIDEBAR_WIDTH_COLLAPSED = 76;

type Child = { label: string; href: string; depth?: number };
type Item = { key: string; label: string; href: string; icon: IconName; children?: Child[]; match: (path: string) => boolean };

export function Shell({
  roles,
  enabled,
  children
}: {
  roles: string[];
  enabled: string[] | null;
  children: ReactNode;
}) {
  const pathname = usePathname();
  const [drawer, setDrawer] = useState(false);
  const [collapsedPref, setCollapsed] = useState(false);
  const [desktop, setDesktop] = useState(true);
  // The rail only exists on desktop; the drawer is always full width.
  const collapsed = collapsedPref && desktop;

  useEffect(() => {
    const query = window.matchMedia("(min-width: 1024px)");
    const update = () => setDesktop(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    try {
      setCollapsed(localStorage.getItem("ss_collapsed") === "1");
    } catch {}
  }, []);
  // A drawer left open across a navigation would cover the page it just opened.
  useEffect(() => setDrawer(false), [pathname]);

  const toggleCollapsed = () =>
    setCollapsed((value) => {
      try {
        localStorage.setItem("ss_collapsed", value ? "0" : "1");
      } catch {}
      return !value;
    });

  return (
    <div className="min-h-screen bg-surface">
      <Sidebar
        roles={roles}
        enabled={enabled}
        pathname={pathname}
        open={drawer}
        collapsed={collapsed}
        onClose={() => setDrawer(false)}
        onToggle={toggleCollapsed}
      />
      <Topbar roles={roles} onMenu={() => setDrawer(true)} collapsed={collapsed} pathname={pathname} />
      <main
        className="min-h-screen pt-16 lg:pl-[var(--ss-sidebar)]"
        style={{ "--ss-sidebar": `${collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH}px` } as React.CSSProperties}
      >
        <div className="mx-auto max-w-7xl p-3 sm:p-4">{children}</div>
      </main>
    </div>
  );
}

function Brand({ rail }: { rail?: boolean }) {
  return (
    <Link href="/" aria-label="SmartSeason overview" className="flex min-w-0 items-center gap-2.5">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/logo-icon.flat.svg" alt="" className="h-[30px] w-[30px] shrink-0" />
      {rail ? null : (
        <span className="truncate font-display text-lg font-bold leading-none">
          <span className="text-gray-900 dark:text-white">Smart</span>
          <span className="text-brand-500">Season</span>
        </span>
      )}
    </Link>
  );
}

const TITLES: Record<string, string> = {
  "/": "Overview",
  "/my-work": "My work",
  "/live": "Live work",
  "/dairy": "Dairy",
  "/team": "Team",
  "/advisor": "Crop advisor",
  "/account": "Account"
};

function pageTitle(pathname: string): string {
  if (TITLES[pathname]) return TITLES[pathname];
  if (pathname.startsWith("/work/")) return "Task";
  const [serviceSlug, entitySlug] = pathname.split("/").filter(Boolean);
  for (const group of GROUPS) {
    const service = group.services.find((candidate) => candidate.slug === serviceSlug);
    if (service) return entitySlug ? service.entities.find((entity) => entity.slug === entitySlug)?.label ?? service.label : service.label;
  }
  return "";
}

function Topbar({ roles, onMenu, collapsed, pathname }: { roles: string[]; onMenu: () => void; collapsed: boolean; pathname: string }) {
  const [dark, setDark] = useState(false);
  const [menu, setMenu] = useState(false);
  const role = primaryRole(roles);

  useEffect(() => setDark(document.documentElement.classList.contains("dark")), []);

  const toggleTheme = () => {
    document.documentElement.classList.toggle("dark");
    try {
      localStorage.setItem("ss-theme", document.documentElement.classList.contains("dark") ? "dark" : "light");
    } catch {}
    setDark((value) => !value);
  };

  return (
    <header className="fixed left-0 right-0 top-0 z-30 flex h-16 items-center gap-3 border-b border-base bg-white px-4 dark:bg-[#0f1612]">
      <button onClick={onMenu} className="btn-ghost !h-9 !w-9 !px-0 lg:hidden" aria-label="Open menu">
        <Icon name="menu" className="h-[18px] w-[18px]" />
      </button>
      <div className="hidden shrink-0 lg:block" style={{ width: collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH }} />
      <div className="hidden truncate font-display text-lg font-semibold text-gray-900 dark:text-white sm:block">{pageTitle(pathname)}</div>
      <div className="flex-1" />
      <button onClick={toggleTheme} className="btn-ghost !h-9 !w-9 !px-0" title="Toggle theme" aria-label="Toggle theme">
        <Icon name={dark ? "sun" : "moon"} className="h-[17px] w-[17px]" />
      </button>
      <div className="relative">
        <button
          onClick={() => setMenu((value) => !value)}
          className="flex h-9 cursor-pointer items-center gap-2 rounded-lg px-2.5 hover:bg-gray-100 dark:hover:bg-white/5"
        >
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-500 text-xs font-bold text-white">
            {ROLE_LABELS[role].charAt(0)}
          </span>
          <span className="hidden text-base font-medium text-gray-700 dark:text-gray-200 sm:block">{ROLE_LABELS[role]}</span>
          <Icon name="chevron" className="h-3.5 w-3.5 text-gray-400" />
        </button>
        {menu ? (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setMenu(false)} />
            <div className="card absolute right-0 top-[calc(100%+8px)] z-50 w-52 py-1">
              <div className="border-b border-base px-4 py-2.5">
                <p className="text-base font-semibold text-gray-900 dark:text-white">{ROLE_LABELS[role]}</p>
                <p className="text-xs text-muted">SmartSeason</p>
              </div>
              <Link
                href="/account"
                onClick={() => setMenu(false)}
                className="flex items-center gap-2.5 px-4 py-2 text-base text-gray-600 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-white/5"
              >
                <Icon name="user" className="h-3.5 w-3.5" />
                Account and password
              </Link>
              <div className="mt-1 border-t border-base">
                <SignOutButton />
              </div>
            </div>
          </>
        ) : null}
      </div>
    </header>
  );
}

function Sidebar({
  roles,
  enabled,
  pathname,
  open,
  collapsed,
  onClose,
  onToggle
}: {
  roles: string[];
  enabled: string[] | null;
  pathname: string;
  open: boolean;
  collapsed: boolean;
  onClose: () => void;
  onToggle: () => void;
}) {
  const segments = pathname.split("/").filter(Boolean);
  const serviceSlug = segments[0] ?? "";
  const entitySlug = segments[1] ?? "";
  const role = primaryRole(roles);

  const groups = GROUPS.filter((group) => canSeeGroup(roles, group.slug))
    .map((group) => ({
      ...group,
      services: group.services.filter(
        (service) => canSeeService(roles, service.slug) && (!enabled || enabled.includes(service.slug))
      )
    }))
    .filter((group) => group.services.length > 0);

  const top: Item[] = [
    { key: "overview", label: "Overview", href: "/", icon: "home", match: (p) => p === "/" }
  ];
  if (canSeeMyWork(roles))
    top.push({ key: "my-work", label: "My work", href: "/my-work", icon: "tasks", match: (p) => p === "/my-work" || p.startsWith("/work/") });
  if (canSeeLiveBoard(roles)) top.push({ key: "live", label: "Live work", href: "/live", icon: "pulse", match: (p) => p === "/live" });
  if (canSeeDairy(roles)) top.push({ key: "dairy", label: "Dairy", href: "/dairy", icon: "farms", match: (p) => p === "/dairy" });
  if (canUse(roles, "team")) top.push({ key: "team", label: "Team", href: "/team", icon: "workforce", match: (p) => p === "/team" });
  if (canSeeAdvisor(roles)) top.push({ key: "advisor", label: "Crop advisor", href: "/advisor", icon: "sparkle", match: (p) => p === "/advisor" });

  const grouped: Item[] = groups.map((group) => ({
    key: group.slug,
    label: group.label,
    href: `/${group.services[0].slug}`,
    icon: group.icon as IconName,
    match: () => group.services.some((service) => service.slug === serviceSlug),
    children: group.services.flatMap((service) => {
      const list: Child[] = [{ label: service.label, href: `/${service.slug}` }];
      if (service.slug === serviceSlug && service.entities.length > 1) {
        for (const entity of service.entities) {
          list.push({ label: entity.label, href: `/${service.slug}/${entity.slug}`, depth: 1 });
        }
      }
      return list;
    })
  }));

  const activeKey = grouped.find((item) => item.match(pathname))?.key;
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set(activeKey ? [activeKey] : []));
  useEffect(() => {
    if (activeKey) setExpanded((current) => (current.has(activeKey) ? current : new Set(current).add(activeKey)));
  }, [activeKey]);
  const toggle = (key: string) =>
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const rail = collapsed;

  const row = (item: Item) => {
    const active = item.match(pathname);
    const children = item.children ?? [];
    const hasChildren = children.length > 0 && !rail;
    const isOpen = expanded.has(item.key);
    return (
      <div key={item.key}>
        <div className={`relative flex items-center !p-0 ${active ? "sidebar-link-active" : "sidebar-link"}`}>
          {active ? <span className="absolute bottom-1/4 left-0 top-1/4 w-0.5 rounded-r bg-brand-500" /> : null}
          <Link
            href={item.href}
            onClick={onClose}
            title={rail ? item.label : undefined}
            className={`flex min-w-0 flex-1 items-center gap-3 px-3 py-2 ${rail ? "justify-center !gap-0 !px-0" : ""}`}
          >
            <Icon name={item.icon} className="h-[17px] w-[17px] shrink-0" />
            {rail ? null : <span className="truncate">{item.label}</span>}
          </Link>
          {hasChildren ? (
            <button
              type="button"
              onClick={() => toggle(item.key)}
              aria-expanded={isOpen}
              aria-label={isOpen ? `Collapse ${item.label}` : `Expand ${item.label}`}
              className="mr-1 shrink-0 cursor-pointer rounded-md p-2 text-gray-400 hover:bg-black/5 dark:hover:bg-white/10"
            >
              <Icon name="chevron" className={`h-3.5 w-3.5 ${isOpen ? "rotate-180" : ""}`} />
            </button>
          ) : null}
        </div>
        {hasChildren && isOpen ? (
          <div className="my-0.5 ml-[26px] space-y-0.5 border-l border-base pl-3">
            {children.map((child) => {
              const on =
                child.depth === 1
                  ? child.href === `/${serviceSlug}/${entitySlug}`
                  : child.href === `/${serviceSlug}` && !entitySlug;
              return (
                <Link
                  key={child.href}
                  href={child.href}
                  onClick={onClose}
                  aria-current={on ? "page" : undefined}
                  className={`flex items-center truncate rounded-md px-2.5 py-1 text-xs ${child.depth === 1 ? "ml-3" : ""} ${
                    on
                      ? "bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/10 dark:text-brand-400"
                      : "text-muted hover:bg-gray-100 hover:text-gray-900 dark:hover:bg-white/5 dark:hover:text-white"
                  }`}
                >
                  <span className="truncate">{child.label}</span>
                </Link>
              );
            })}
          </div>
        ) : null}
      </div>
    );
  };

  return (
    <>
      {open ? <div onClick={onClose} className="fixed inset-0 z-40 bg-black/50 lg:hidden" /> : null}
      <aside
        className={`fixed inset-y-0 left-0 z-50 flex flex-col border-r border-base bg-surface-2 lg:translate-x-0 ${
          open ? "translate-x-0" : "-translate-x-full"
        }`}
        style={{ width: collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH }}
        data-rail={rail ? "true" : "false"}
      >
        <div className={`flex h-16 shrink-0 items-center border-b border-base px-4 ${rail ? "justify-center !px-2" : "justify-between"}`}>
          <Brand rail={rail} />
          {rail ? null : (
            <button
              onClick={onClose}
              className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-gray-500 hover:bg-gray-200 dark:hover:bg-gray-700 lg:hidden"
              aria-label="Close menu"
            >
              <Icon name="close" className="h-4 w-4" />
            </button>
          )}
        </div>

        <nav className="flex-1 space-y-px overflow-y-auto overflow-x-hidden px-2.5 py-2" aria-label="Main">
          {top.map(row)}
          {grouped.length > 0 ? (
            <div className={`pb-1 pt-4 ${rail ? "flex justify-center" : "px-3"}`}>
              {rail ? (
                <div className="w-6 border-t border-base" />
              ) : (
                <p className="text-2xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-600">Services</p>
              )}
            </div>
          ) : null}
          {grouped.map(row)}
        </nav>

        <button
          onClick={onToggle}
          className={`hidden h-10 shrink-0 cursor-pointer items-center border-t border-base text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-white/5 dark:hover:text-gray-200 lg:flex ${
            rail ? "justify-center" : "justify-start gap-2 px-4 text-xs font-medium"
          }`}
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          {collapsed ? (
            <Icon name="expand" className="h-4 w-4" />
          ) : (
            <>
              <Icon name="collapse" className="h-4 w-4" />
              Collapse
            </>
          )}
        </button>

        <div className="shrink-0 border-t border-base p-3">
          <Link
            href="/account"
            onClick={onClose}
            title={rail ? "Account and password" : undefined}
            className={`flex items-center gap-3 rounded-lg p-2.5 hover:bg-gray-100 dark:hover:bg-white/5 ${rail ? "justify-center !p-2" : ""}`}
          >
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-500 text-2xs font-bold text-white">
              {ROLE_LABELS[role].charAt(0)}
            </span>
            {rail ? null : (
              <span className="min-w-0 flex-1">
                <span className="block truncate text-xs font-semibold text-gray-900 dark:text-white">{ROLE_LABELS[role]}</span>
                <span className="block truncate text-2xs text-muted">Account and password</span>
              </span>
            )}
          </Link>
        </div>
      </aside>
    </>
  );
}

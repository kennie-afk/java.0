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
  canSeeLiveBoard,
  canSeeMyWork,
  canSeeService,
  primaryRole,
  ROLE_LABELS
} from "@/lib/roles";

/**
 * One labelled sidebar. A short "Workspace" block holds the screens that are about the
 * person (overview, their work, the live board), and below it every service group the
 * role may read, each collapsible, with the active service's entities nested under it.
 *
 * What appears is decided by the same generated catalogue and role matrix the
 * controllers are annotated from, so the menu cannot offer what the service refuses.
 * On small screens the same content opens as a drawer behind a top bar.
 */
export function Nav({ roles, enabled }: { roles: string[]; enabled: string[] | null }) {
  const pathname = usePathname();
  const [drawer, setDrawer] = useState(false);

  // A drawer left open across a navigation would cover the page it just opened.
  useEffect(() => setDrawer(false), [pathname]);

  return (
    <>
      <aside className="sticky top-0 hidden h-screen w-[272px] shrink-0 flex-col border-r border-[var(--color-line)] bg-[var(--color-rail)] lg:flex">
        <SidebarBody roles={roles} enabled={enabled} pathname={pathname} />
      </aside>

      <header className="sticky top-0 z-40 flex h-14 items-center justify-between border-b border-[var(--color-line)] bg-[var(--color-rail)] px-4 lg:hidden">
        <Brand />
        <button
          type="button"
          aria-label="Open menu"
          onClick={() => setDrawer(true)}
          className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-lg text-[var(--color-ink)] hover:bg-[var(--color-raised)]"
        >
          <Icon name="menu" className="h-6 w-6" />
        </button>
      </header>

      {drawer ? (
        <div className="fixed inset-0 z-[70] lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <button
            type="button"
            aria-label="Close menu"
            className="absolute inset-0 cursor-default bg-[rgba(18,32,26,0.45)]"
            onClick={() => setDrawer(false)}
          />
          <aside className="absolute inset-y-0 left-0 flex w-[300px] max-w-[86vw] flex-col bg-[var(--color-rail)] shadow-[var(--shadow-lift)]">
            <button
              type="button"
              aria-label="Close menu"
              onClick={() => setDrawer(false)}
              className="absolute right-2 top-3 flex h-9 w-9 cursor-pointer items-center justify-center rounded-lg text-[var(--color-muted)] hover:bg-[var(--color-raised)]"
            >
              <Icon name="close" className="h-5 w-5" />
            </button>
            <SidebarBody roles={roles} enabled={enabled} pathname={pathname} />
          </aside>
        </div>
      ) : null}
    </>
  );
}

function Brand() {
  return (
    <Link href="/" aria-label="SmartSeason overview" className="flex items-center gap-2.5">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/logo-icon.flat.svg" alt="" className="h-9 w-9" />
      <span className="leading-tight">
        <span className="block font-[family-name:var(--font-display)] text-lg font-semibold tracking-[-0.01em]">
          SmartSeason
        </span>
        <span className="block text-xs text-[var(--color-muted)]">Farm operations</span>
      </span>
    </Link>
  );
}

function SidebarBody({
  roles,
  enabled,
  pathname
}: {
  roles: string[];
  enabled: string[] | null;
  pathname: string;
}) {
  const segments = pathname.split("/").filter(Boolean);
  const serviceSlug = segments[0] ?? "";
  const entitySlug = segments[1] ?? "";

  const visibleGroups = GROUPS
    .filter((group) => canSeeGroup(roles, group.slug))
    .map((group) => ({
      ...group,
      services: group.services.filter(
        (service) => canSeeService(roles, service.slug) && (!enabled || enabled.includes(service.slug))
      )
    }))
    .filter((group) => group.services.length > 0);

  const activeGroupSlug = visibleGroups.find((group) =>
    group.services.some((service) => service.slug === serviceSlug)
  )?.slug;
  const [open, setOpen] = useState<Set<string>>(() => new Set(activeGroupSlug ? [activeGroupSlug] : []));

  // Opening a service from anywhere (overview card, link in a page) should show where it lives.
  useEffect(() => {
    if (activeGroupSlug) {
      setOpen((current) => (current.has(activeGroupSlug) ? current : new Set(current).add(activeGroupSlug)));
    }
  }, [activeGroupSlug]);

  const toggle = (slug: string) =>
    setOpen((current) => {
      const next = new Set(current);
      if (next.has(slug)) next.delete(slug);
      else next.add(slug);
      return next;
    });

  const role = primaryRole(roles);

  return (
    <>
      <div className="border-b border-[var(--color-line)] px-5 py-4">
        <Brand />
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4" aria-label="Main">
        <SectionLabel>Workspace</SectionLabel>
        <div className="mb-5 flex flex-col gap-0.5">
          <NavLink href="/" icon="home" label="Overview" active={pathname === "/"} />
          {canSeeMyWork(roles) ? (
            <NavLink
              href="/my-work"
              icon="tasks"
              label="My work"
              active={pathname === "/my-work" || pathname.startsWith("/work/")}
            />
          ) : null}
          {canSeeLiveBoard(roles) ? (
            <NavLink href="/live" icon="pulse" label="Live work" active={pathname === "/live"} />
          ) : null}
          {canUse(roles, "team") ? (
            <NavLink href="/team" icon="workforce" label="Team" active={pathname === "/team"} />
          ) : null}
          {canSeeAdvisor(roles) ? (
            <NavLink href="/advisor" icon="sparkle" label="Crop advisor" active={pathname === "/advisor"} />
          ) : null}
        </div>

        {visibleGroups.map((group) => {
          const expanded = open.has(group.slug);
          const containsActive = group.slug === activeGroupSlug;
          return (
            <div key={group.slug} className="mb-1">
              <button
                type="button"
                onClick={() => toggle(group.slug)}
                aria-expanded={expanded}
                className={`flex w-full cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm font-semibold transition-colors hover:bg-[var(--color-raised)] ${
                  containsActive ? "text-[var(--color-accent-deep)]" : "text-[var(--color-ink)]"
                }`}
              >
                <Icon name={group.icon as IconName} className="h-[18px] w-[18px] shrink-0" />
                <span className="flex-1">{group.label}</span>
                <span className="text-xs font-medium tabular-nums text-[var(--color-faint)]">
                  {group.services.length}
                </span>
                <Icon
                  name="chevron"
                  className={`h-4 w-4 shrink-0 text-[var(--color-faint)] transition-transform ${
                    expanded ? "" : "-rotate-90"
                  }`}
                />
              </button>

              {expanded ? (
                <div className="ml-[21px] mt-0.5 border-l border-[var(--color-line)] pl-2">
                  {group.services.map((service) => {
                    const current = service.slug === serviceSlug;
                    return (
                      <div key={service.slug}>
                        <Link
                          href={`/${service.slug}`}
                          aria-current={current ? "page" : undefined}
                          className={`block rounded-lg px-3 py-1.5 text-sm transition-colors ${
                            current
                              ? "bg-[var(--color-accent-soft)] font-semibold text-[var(--color-accent-deep)]"
                              : "text-[var(--color-muted)] hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)]"
                          }`}
                        >
                          {service.label}
                        </Link>
                        {current && service.entities.length > 1 ? (
                          <div className="mb-1 ml-3 mt-0.5 flex flex-col border-l border-[var(--color-line)] pl-2">
                            {service.entities.map((entity) => {
                              const on = entity.slug === entitySlug;
                              return (
                                <Link
                                  key={entity.slug}
                                  href={`/${service.slug}/${entity.slug}`}
                                  aria-current={on ? "page" : undefined}
                                  className={`rounded-md px-2.5 py-1 text-sm transition-colors ${
                                    on
                                      ? "font-semibold text-[var(--color-accent)]"
                                      : "text-[var(--color-muted)] hover:text-[var(--color-ink)]"
                                  }`}
                                >
                                  {entity.label}
                                </Link>
                              );
                            })}
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              ) : null}
            </div>
          );
        })}
      </nav>

      <div className="border-t border-[var(--color-line)] p-3">
        <Link
          href="/account"
          className={`mb-0.5 flex items-center gap-3 rounded-lg px-3 py-2 transition-colors ${
            pathname === "/account"
              ? "bg-[var(--color-accent-soft)]"
              : "hover:bg-[var(--color-raised)]"
          }`}
        >
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[var(--color-accent)] text-sm font-bold text-white">
            {ROLE_LABELS[role].charAt(0)}
          </span>
          <span className="leading-tight">
            <span className="block text-sm font-semibold">{ROLE_LABELS[role]}</span>
            <span className="block text-xs text-[var(--color-muted)]">Account and password</span>
          </span>
        </Link>
        <SignOutButton />
      </div>
    </>
  );
}

function SectionLabel({ children }: { children: ReactNode }) {
  return (
    <p className="px-3 pb-1.5 text-xs font-semibold uppercase tracking-[0.08em] text-[var(--color-faint)]">
      {children}
    </p>
  );
}

function NavLink({
  href,
  icon,
  label,
  active
}: {
  href: string;
  icon: IconName;
  label: string;
  active: boolean;
}) {
  return (
    <Link
      href={href}
      aria-current={active ? "page" : undefined}
      className={`relative flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-semibold transition-colors ${
        active
          ? "bg-[var(--color-accent-soft)] text-[var(--color-accent-deep)]"
          : "text-[var(--color-ink)] hover:bg-[var(--color-raised)]"
      }`}
    >
      {active ? (
        <span className="absolute inset-y-1.5 left-0 w-[3px] rounded-r bg-[var(--color-accent)]" aria-hidden="true" />
      ) : null}
      <Icon name={icon} className="h-[18px] w-[18px] shrink-0" />
      {label}
    </Link>
  );
}

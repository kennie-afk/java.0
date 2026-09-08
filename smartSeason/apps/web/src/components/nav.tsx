"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
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
 * Two-level navigation: an icon rail for the seven service groups, and a dense
 * text column listing the services in whichever group is open, with the active
 * service's entities nested underneath it.
 *
 * Twenty-seven services will not fit in a flat rail, and a flyout would need
 * hover motion, which the house style does not use.
 */
export function Nav({ roles }: { roles: string[] }) {
  const pathname = usePathname();
  const segments = pathname.split("/").filter(Boolean);
  const serviceSlug = segments[0] ?? "";
  const entitySlug = segments[1] ?? "";

  const visibleGroups = GROUPS
    .filter((group) => canSeeGroup(roles, group.slug))
    .map((group) => ({
      ...group,
      services: group.services.filter((service) => canSeeService(roles, service.slug))
    }));
  const activeGroup =
    visibleGroups.find((group) => group.services.some((service) => service.slug === serviceSlug)) ??
    visibleGroups[0];
  const onOverview = pathname === "/";
  const onTaskPage =
    pathname === "/my-work" ||
    pathname === "/live" ||
    pathname === "/advisor" ||
    pathname === "/account" ||
    pathname === "/team" ||
    pathname.startsWith("/work/");

  return (
    <>
      <aside className="sticky top-0 hidden h-screen w-[72px] shrink-0 flex-col border-r border-[var(--color-line)] bg-[var(--color-rail)] md:flex">
        <div className="flex justify-center py-4">
          <Link href="/" aria-label="SmartSeason overview" className="block">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/logo-icon.flat.svg" alt="SmartSeason" className="h-8 w-8" />
          </Link>
        </div>

        <nav className="flex flex-1 flex-col gap-0.5 px-2">
          <RailLink href="/" icon="home" label="Overview" active={onOverview} />
          {canSeeMyWork(roles) ? (
            <RailLink
              href="/my-work"
              icon="workforce"
              label="My work"
              active={pathname === "/my-work" || pathname.startsWith("/work/")}
            />
          ) : null}
          {canSeeLiveBoard(roles) ? (
            <RailLink
              href="/live"
              icon="seasons"
              label="Live"
              active={pathname === "/live"}
            />
          ) : null}
          {canUse(roles, "team") ? (
            <RailLink
              href="/team"
              icon="identity"
              label="Team"
              active={pathname === "/team"}
            />
          ) : null}
          {canSeeAdvisor(roles) ? (
            <RailLink
              href="/advisor"
              icon="farms"
              label="Advisor"
              active={pathname === "/advisor"}
            />
          ) : null}
          {visibleGroups.map((group) => (
            <RailLink
              key={group.slug}
              href={`/${group.services[0].slug}`}
              icon={group.icon as IconName}
              label={group.label}
              active={!onOverview && !onTaskPage && group.slug === activeGroup?.slug}
            />
          ))}
        </nav>

        <div className="px-2 pb-5 pt-2">
          <Link
            href="/account"
            className={`mb-1.5 block rounded-lg py-1 text-center text-2xs transition-colors ${
              pathname === "/account"
                ? "bg-[var(--color-good-soft)] text-[var(--color-good)]"
                : "text-[var(--color-faint)] hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)]"
            }`}
          >
            {ROLE_LABELS[primaryRole(roles)]}
          </Link>
          <SignOutButton />
        </div>
      </aside>

      {onOverview || onTaskPage || !activeGroup ? null : (
        <aside className="sticky top-0 hidden h-screen w-[168px] shrink-0 overflow-y-auto border-r border-[var(--color-line)] bg-[var(--color-surface)] py-4 lg:block">
          <p className="px-3 pb-2 text-2xs font-medium uppercase tracking-[0.07em] text-[var(--color-faint)]">
            {activeGroup.label}
          </p>
          <nav className="flex flex-col">
            {activeGroup.services.map((service) => {
              const current = service.slug === serviceSlug;
              return (
                <div key={service.slug}>
                  <Link
                    href={`/${service.slug}`}
                    className={`block px-3 py-1.5 text-xs transition-colors ${
                      current
                        ? "font-medium text-[var(--color-ink)]"
                        : "text-[var(--color-muted)] hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)]"
                    }`}
                  >
                    {service.label}
                  </Link>
                  {current ? (
                    <div className="mb-1 flex flex-col border-l border-[var(--color-line)] pl-0">
                      {service.entities.map((entity) => {
                        const on = entity.slug === entitySlug;
                        return (
                          <Link
                            key={entity.slug}
                            href={`/${service.slug}/${entity.slug}`}
                            aria-current={on ? "page" : undefined}
                            className={`-ml-px border-l-2 py-1 pl-4 pr-3 text-2xs transition-colors ${
                              on
                                ? "border-[var(--color-good)] bg-[var(--color-good-soft)] font-medium text-[var(--color-good)]"
                                : "border-transparent text-[var(--color-muted)] hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)]"
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
          </nav>
        </aside>
      )}
    </>
  );
}

function RailLink({
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
      className={`flex flex-col items-center gap-0.5 rounded-lg px-1 py-2 text-2xs font-medium transition-colors ${
        active
          ? "bg-[var(--color-good-soft)] text-[var(--color-good)]"
          : "text-[var(--color-muted)] hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)]"
      }`}
    >
      <Icon name={icon} className="h-[17px] w-[17px]" />
      <span className="text-center leading-tight">{label}</span>
    </Link>
  );
}

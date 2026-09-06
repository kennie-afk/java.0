"use client";

import Link from "next/link";
import { Logo } from "@/components/logo";
import { usePathname } from "next/navigation";
import { signOut } from "@/app/actions";

export interface NavItem {
  href: string;
  label: string;
}

export function Topbar({
  items,
  fullName,
  role,
  organisation
}: {
  items: NavItem[];
  fullName: string;
  role: string;
  organisation: string;
}) {
  const pathname = usePathname();
  const initials = fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join("");

  return (
    <header className="sticky top-0 z-20 border-b border-[var(--color-line)] bg-[var(--color-surface)]">
      <div className="mx-auto flex max-w-6xl items-center gap-6 px-5 py-3">
        <Link href="/" className="flex items-center gap-2.5">
          <Logo className="h-7 w-7" />
          <span className="text-[0.9375rem] font-semibold tracking-[-0.01em]">Soko</span>
        </Link>

        <nav className="hidden flex-1 items-center gap-1 md:flex">
          {items.map((item) => {
            const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`rounded-full px-3.5 py-1.5 text-[0.8125rem] font-medium transition-colors ${
                  active
                    ? "bg-[var(--color-ink)] text-white"
                    : "text-[var(--color-muted)] hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)]"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-3">
          <div className="hidden text-right sm:block">
            <p className="text-[0.8125rem] font-medium leading-tight">{organisation}</p>
            <p className="text-[0.6875rem] capitalize leading-tight text-[var(--color-faint)]">
              {fullName} · {role.toLowerCase()}
            </p>
          </div>
          <span
            title={fullName}
            className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-accent-soft)] text-[0.75rem] font-semibold text-[var(--color-accent)]"
          >
            {initials}
          </span>
          <form action={signOut}>
            <button
              type="submit"
              className="rounded-full border border-[var(--color-line)] px-3 py-1.5 text-[0.75rem] font-medium text-[var(--color-muted)] transition-colors hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)]"
            >
              Sign out
            </button>
          </form>
        </div>
      </div>

      <nav className="flex gap-1 overflow-x-auto border-t border-[var(--color-line)] px-5 py-2 md:hidden">
        {items.map((item) => {
          const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`whitespace-nowrap rounded-full px-3 py-1.5 text-[0.75rem] font-medium ${
                active ? "bg-[var(--color-ink)] text-white" : "text-[var(--color-muted)]"
              }`}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
    </header>
  );
}

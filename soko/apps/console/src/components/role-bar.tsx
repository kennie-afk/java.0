"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Logo } from "@/components/logo";
import { signOut } from "@/app/actions";

export function RoleBar({
  badge,
  items,
  fullName,
  organisation
}: {
  badge: string;
  items: { href: string; label: string }[];
  fullName: string;
  organisation: string;
}) {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-20 border-b border-[var(--color-line)] bg-[var(--color-surface)]">
      <div className="mx-auto flex max-w-5xl items-center gap-5 px-5 py-3">
        <span className="flex items-center gap-2.5">
          <Logo className="h-7 w-7" />
          <span className="text-[0.9375rem] font-semibold tracking-[-0.01em]">Soko</span>
        </span>
        <span className="rounded-full bg-[var(--color-amber-soft)] px-2.5 py-0.5 text-[0.6875rem] font-medium text-[var(--color-amber)]">
          {badge}
        </span>

        <nav className="flex flex-1 items-center gap-1">
          {items.map((item) => {
            const active =
              item.href === items[0]!.href ? pathname === item.href : pathname.startsWith(item.href);
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

        <div className="hidden text-right sm:block">
          <p className="text-[0.8125rem] font-medium leading-tight">{organisation || fullName}</p>
          <p className="text-[0.6875rem] leading-tight text-[var(--color-faint)]">{fullName}</p>
        </div>
        <form action={signOut}>
          <button
            type="submit"
            className="rounded-full border border-[var(--color-line)] px-3 py-1.5 text-[0.75rem] font-medium text-[var(--color-muted)] transition-colors hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)]"
          >
            Sign out
          </button>
        </form>
      </div>
    </header>
  );
}

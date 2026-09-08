import Link from "next/link";
import { PageHeader, Stat } from "@/components/ui";
import { api, type PageResponse } from "@/lib/api";
import { GROUPS } from "@/lib/catalogue.generated";
import { canSeeGroup, canSeeService, primaryRole, ROLE_LABELS, type Role } from "@/lib/roles";
import { loadMyWork } from "@/lib/tasks";
import { readRoles, readToken } from "@/lib/session";

async function count(path: string, token: string | null): Promise<number | null> {
  try {
    return (await api.get<PageResponse<unknown>>(`${path}?size=1`, token)).totalElements;
  } catch {
    return null;
  }
}

interface Headline {
  label: string;
  path: string;
  href: string;
  hint?: string;
}

/**
 * What each role opens the platform to see.
 *
 * A farmer cares what is growing and what it will fetch; a storekeeper cares
 * what is in the shed; finance cares what is owed. Showing all four to all four
 * is how a dashboard becomes wallpaper.
 */
const HEADLINES: Record<Role, Headline[]> = {
  ADMIN: [
    { label: "Farms", path: "/api/farm/v1/farms", href: "/farm/farms" },
    { label: "Workers", path: "/api/workforce/v1/workers", href: "/workforce/workers" },
    { label: "Listings", path: "/api/marketplace/v1/supply-listings", href: "/marketplace/supply-listings" },
    { label: "Orders", path: "/api/order/v1/orders", href: "/order/orders" },
    { label: "Fraud cases", path: "/api/fraud/v1/fraud-cases", href: "/fraud/fraud-cases" },
    { label: "Users", path: "/api/identity/v1/users", href: "/identity/users" }
  ],
  FARMER: [
    { label: "Farms", path: "/api/farm/v1/farms", href: "/farm/farms" },
    { label: "Seasons", path: "/api/season/v1/seasons", href: "/season/seasons" },
    { label: "Workers", path: "/api/workforce/v1/workers", href: "/workforce/workers" },
    { label: "Listings", path: "/api/marketplace/v1/supply-listings", href: "/marketplace/supply-listings" },
    { label: "Orders", path: "/api/order/v1/orders", href: "/order/orders" },
    { label: "Fraud cases", path: "/api/fraud/v1/fraud-cases", href: "/fraud/fraud-cases", hint: "Open against your workers" }
  ],
  MANAGER: [
    { label: "Work orders", path: "/api/task/v1/work-orders", href: "/task/work-orders" },
    { label: "Assignments", path: "/api/task/v1/task-assignments", href: "/task/task-assignments" },
    { label: "Workers", path: "/api/workforce/v1/workers", href: "/workforce/workers" },
    { label: "Shifts", path: "/api/attendance/v1/shifts", href: "/attendance/shifts" },
    { label: "Seasons", path: "/api/season/v1/seasons", href: "/season/seasons" },
    { label: "Fraud cases", path: "/api/fraud/v1/fraud-cases", href: "/fraud/fraud-cases" }
  ],
  AGRONOMIST: [
    { label: "Advisories", path: "/api/agronomy/v1/advisories", href: "/agronomy/advisories" },
    { label: "Scouting reports", path: "/api/agronomy/v1/scouting-reports", href: "/agronomy/scouting-reports" },
    { label: "Seasons", path: "/api/season/v1/seasons", href: "/season/seasons" },
    { label: "Pest library", path: "/api/agronomy/v1/pest-diseases", href: "/agronomy/pest-diseases" },
    { label: "Weather stations", path: "/api/weather/v1/weather-stations", href: "/weather/weather-stations" },
    { label: "Forecasts", path: "/api/weather/v1/forecasts", href: "/weather/forecasts" }
  ],
  STOREKEEPER: [
    { label: "Warehouses", path: "/api/inventory/v1/warehouses", href: "/inventory/warehouses" },
    { label: "Batches", path: "/api/inventory/v1/batches", href: "/inventory/batches" },
    { label: "Stock items", path: "/api/inventory/v1/stock-items", href: "/inventory/stock-items" },
    { label: "Transport jobs", path: "/api/logistics/v1/transport-jobs", href: "/logistics/transport-jobs" },
    { label: "Vehicles", path: "/api/logistics/v1/vehicles", href: "/logistics/vehicles" },
    { label: "Trace batches", path: "/api/traceability/v1/trace-batches", href: "/traceability/trace-batches" }
  ],
  FINANCE: [
    { label: "Payments", path: "/api/payment/v1/payment-intents", href: "/payment/payment-intents" },
    { label: "Settlements", path: "/api/payout/v1/settlements", href: "/payout/settlements" },
    { label: "Payout batches", path: "/api/payout/v1/payout-batches", href: "/payout/payout-batches" },
    { label: "Accounts", path: "/api/ledger/v1/accounts", href: "/ledger/accounts" },
    { label: "Journal entries", path: "/api/ledger/v1/journal-entries", href: "/ledger/journal-entries" },
    { label: "Holds", path: "/api/payout/v1/payout-holds", href: "/payout/payout-holds", hint: "Withheld pending review" }
  ],
  BUYER: [
    { label: "Listings", path: "/api/marketplace/v1/supply-listings", href: "/marketplace/supply-listings" },
    { label: "My orders", path: "/api/order/v1/orders", href: "/order/orders" },
    { label: "Offers", path: "/api/marketplace/v1/offers", href: "/marketplace/offers" },
    { label: "Prices", path: "/api/pricing/v1/price-series", href: "/pricing/price-series" },
    { label: "Deliveries", path: "/api/logistics/v1/transport-jobs", href: "/logistics/transport-jobs" },
    { label: "Payments", path: "/api/payment/v1/payment-intents", href: "/payment/payment-intents" }
  ],
  // A worker's count comes from the scoped endpoint, not the collection: the
  // collection would answer with the whole tenant's assignments.
  WORKER: []
};

const SUBTITLE: Record<Role, string> = {
  ADMIN: "Everything across the platform.",
  FARMER: "What is growing, who is working it, and what it is fetching.",
  MANAGER: "Today's work and the people doing it.",
  AGRONOMIST: "Crop health, advisories and the weather behind them.",
  STOREKEEPER: "What is in store and what is moving.",
  FINANCE: "Money in, money out, and anything on hold.",
  BUYER: "Produce on offer and the orders you have placed.",
  WORKER: "Your assigned work."
};

export default async function OverviewPage() {
  const [token, roles] = await Promise.all([readToken(), readRoles()]);
  const role = primaryRole(roles);
  const headlines = HEADLINES[role];

  const counts = await Promise.all(headlines.map((item) => count(item.path, token)));

  // Counted through /my-work so the figure matches what the worker can open.
  const myTaskCount =
    role === "WORKER" ? (await loadMyWork()).filter((card) => !card.assignment.completedAt).length : null;

  // The service map is only meaningful to someone who can open more than a
  // couple of services, so a worker never sees it.
  const visibleGroups = GROUPS.filter((group) => canSeeGroup(roles, group.slug)).map((group) => ({
    ...group,
    services: group.services.filter((service) => canSeeService(roles, service.slug))
  }));

  return (
    <>
      <PageHeader title={`${ROLE_LABELS[role]} overview`} subtitle={SUBTITLE[role]} />

      <section className="grid gap-2.5 sm:grid-cols-3 xl:grid-cols-6">
        {myTaskCount !== null ? (
          <Link href="/my-work" className="block">
            <Stat label="My open tasks" value={String(myTaskCount)} hint="Assigned to you" />
          </Link>
        ) : null}
        {headlines.map((item, index) => (
          <Link key={item.label} href={item.href} className="block">
            <Stat
              label={item.label}
              value={counts[index]?.toLocaleString() ?? "—"}
              hint={item.hint}
            />
          </Link>
        ))}
      </section>

      {visibleGroups.length > 1 ? (
        <section className="mt-6 grid gap-2.5 lg:grid-cols-2 xl:grid-cols-3">
          {visibleGroups.map((group) => (
            <div
              key={group.slug}
              className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)]"
            >
              <div className="flex items-baseline justify-between border-b border-[var(--color-line)] px-3 py-2">
                <h2 className="text-xs font-semibold">{group.label}</h2>
                <span className="text-2xs text-[var(--color-faint)]">
                  {group.services.length} service{group.services.length === 1 ? "" : "s"}
                </span>
              </div>
              <ul>
                {group.services.map((service) => (
                  <li key={service.slug}>
                    <Link
                      href={`/${service.slug}`}
                      className="flex items-center justify-between gap-3 px-3 py-1.5 text-xs transition-colors hover:bg-[var(--color-raised)]"
                    >
                      <span className="truncate">{service.label}</span>
                      <span className="shrink-0 text-2xs uppercase tracking-[0.06em] text-[var(--color-faint)]">
                        {service.access[role] ?? "full"}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </section>
      ) : null}
    </>
  );
}

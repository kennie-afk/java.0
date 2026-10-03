import Link from "next/link";
import { Badge, Card, Meter, PageHeader, Stat } from "@/components/ui";
import { Icon, type IconName } from "@/components/icons";
import { api, type PageResponse } from "@/lib/api";
import { GROUPS } from "@/lib/catalogue.generated";
import { canSeeGroup, canSeeLiveBoard, canSeeService, primaryRole, ROLE_LABELS, type Role } from "@/lib/roles";
import { clockTime, formatDuration, loadMyWork, loadTaskBoard } from "@/lib/tasks";
import { enabledServices, serviceOfPath } from "@/lib/deployment";
import { readRoles, readToken } from "@/lib/session";
import { loadCollection } from "@/lib/load";
import { loadRecord } from "@/lib/record";
import { crop, kg, progressOf, type Season } from "@/lib/season";

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
  icon: IconName;
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
    { label: "Farms", path: "/api/farm/v1/farms", href: "/farm/farms", icon: "farms" },
    { label: "Workers", path: "/api/workforce/v1/workers", href: "/workforce/workers", icon: "workforce" },
    { label: "Orders", path: "/api/order/v1/orders", href: "/order/orders", icon: "marketplace" },
    { label: "Fraud cases", path: "/api/fraud/v1/fraud-cases", href: "/fraud/fraud-cases", icon: "fraud" },
    { label: "Users", path: "/api/identity/v1/users", href: "/identity/users", icon: "identity" }
  ],
  FARMER: [
    { label: "Farms", path: "/api/farm/v1/farms", href: "/farm/farms", icon: "farms" },
    { label: "Seasons", path: "/api/season/v1/seasons", href: "/season/seasons", icon: "seasons" },
    { label: "Workers", path: "/api/workforce/v1/workers", href: "/workforce/workers", icon: "workforce" },
    { label: "Orders", path: "/api/order/v1/orders", href: "/order/orders", icon: "marketplace" },
    { label: "Fraud cases", path: "/api/fraud/v1/fraud-cases", href: "/fraud/fraud-cases", hint: "Open against your workers", icon: "fraud" }
  ],
  MANAGER: [
    { label: "Work orders", path: "/api/task/v1/work-orders", href: "/task/work-orders", icon: "tasks" },
    { label: "Assignments", path: "/api/task/v1/task-assignments", href: "/task/task-assignments", icon: "pulse" },
    { label: "Workers", path: "/api/workforce/v1/workers", href: "/workforce/workers", icon: "workforce" },
    { label: "Shifts", path: "/api/attendance/v1/shifts", href: "/attendance/shifts", icon: "seasons" },
    { label: "Seasons", path: "/api/season/v1/seasons", href: "/season/seasons", icon: "seasons" },
    { label: "Fraud cases", path: "/api/fraud/v1/fraud-cases", href: "/fraud/fraud-cases", icon: "fraud" }
  ],
  AGRONOMIST: [
    { label: "Advisories", path: "/api/agronomy/v1/advisories", href: "/agronomy/advisories", icon: "sparkle" },
    { label: "Scouting reports", path: "/api/agronomy/v1/scouting-reports", href: "/agronomy/scouting-reports", icon: "farms" },
    { label: "Seasons", path: "/api/season/v1/seasons", href: "/season/seasons", icon: "seasons" },
    { label: "Pest library", path: "/api/agronomy/v1/pest-diseases", href: "/agronomy/pest-diseases", icon: "fraud" },
    { label: "Weather stations", path: "/api/weather/v1/weather-stations", href: "/weather/weather-stations", icon: "platform" },
    { label: "Forecasts", path: "/api/weather/v1/forecasts", href: "/weather/forecasts", icon: "platform" }
  ],
  STOREKEEPER: [
    { label: "Warehouses", path: "/api/inventory/v1/warehouses", href: "/inventory/warehouses", icon: "platform" },
    { label: "Batches", path: "/api/inventory/v1/batches", href: "/inventory/batches", icon: "marketplace" },
    { label: "Stock items", path: "/api/inventory/v1/stock-items", href: "/inventory/stock-items", icon: "marketplace" },
    { label: "Transport jobs", path: "/api/logistics/v1/transport-jobs", href: "/logistics/transport-jobs", icon: "marketplace" },
    { label: "Vehicles", path: "/api/logistics/v1/vehicles", href: "/logistics/vehicles", icon: "marketplace" },
    { label: "Trace batches", path: "/api/traceability/v1/trace-batches", href: "/traceability/trace-batches", icon: "farms" }
  ],
  FINANCE: [
    { label: "Payments", path: "/api/payment/v1/payment-intents", href: "/payment/payment-intents", icon: "money" },
    { label: "Settlements", path: "/api/payout/v1/settlements", href: "/payout/settlements", icon: "money" },
    { label: "Payout batches", path: "/api/payout/v1/payout-batches", href: "/payout/payout-batches", icon: "money" },
    { label: "Accounts", path: "/api/ledger/v1/accounts", href: "/ledger/accounts", icon: "money" },
    { label: "Journal entries", path: "/api/ledger/v1/journal-entries", href: "/ledger/journal-entries", icon: "money" },
    { label: "Holds", path: "/api/payout/v1/payout-holds", href: "/payout/payout-holds", hint: "Withheld pending review", icon: "alert" }
  ],
  BUYER: [
    { label: "My orders", path: "/api/order/v1/orders", href: "/order/orders", icon: "marketplace" },
    { label: "Prices", path: "/api/pricing/v1/price-series", href: "/pricing/price-series", icon: "money" },
    { label: "Deliveries", path: "/api/logistics/v1/transport-jobs", href: "/logistics/transport-jobs", icon: "marketplace" },
    { label: "Payments", path: "/api/payment/v1/payment-intents", href: "/payment/payment-intents", icon: "money" }
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
  BUYER: "The orders you have placed and what is on its way to you.",
  WORKER: "Your assigned work."
};

export default async function OverviewPage() {
  const [token, roles] = await Promise.all([readToken(), readRoles()]);
  const role = primaryRole(roles);
  const enabled = enabledServices();
  const headlines = HEADLINES[role].filter((item) => !enabled || enabled.includes(serviceOfPath(item.path)));

  const counts = await Promise.all(headlines.map((item) => count(item.path, token)));

  // Counted through /my-work so the figure matches what the worker can open.
  const myTaskCount =
    role === "WORKER" ? (await loadMyWork()).filter((card) => !card.assignment.completedAt).length : null;

  // The service map is only meaningful to someone who can open more than a
  // couple of services, so a worker never sees it.
  const visibleGroups = GROUPS.filter((group) => canSeeGroup(roles, group.slug)).map((group) => ({
    ...group,
    services: group.services.filter(
      (service) => canSeeService(roles, service.slug) && (!enabled || enabled.includes(service.slug))
    )
  })).filter((group) => group.services.length > 0);

  const board = canSeeLiveBoard(roles) && (!enabled || enabled.includes("task")) ? (await loadTaskBoard()).cards : [];
  const inProgress = [...board]
    .sort((x, y) => Number(y.running) - Number(x.running) || (y.assignment.assignedAt ?? "").localeCompare(x.assignment.assignedAt ?? ""))
    .slice(0, 6);
  // Crops in the ground, soonest harvest first. Only for roles that can read seasons, and
  // only when the season service is actually running.
  const showCrops = canSeeService(roles, "season") && (!enabled || enabled.includes("season"));
  const crops = showCrops
    ? await loadCollection<Season>("/api/season/v1/seasons?status=ACTIVE", 0, "expectedHarvestDate,asc")
    : null;
  const shownCrops = (crops?.rows ?? []).slice(0, 8);
  const farmNames = new Map<string, string>();
  if (shownCrops.length > 0 && canSeeService(roles, "farm") && (!enabled || enabled.includes("farm"))) {
    const farmIds = [...new Set(shownCrops.map((season) => season.farmId))];
    const farms = await Promise.all(farmIds.map((farmId) => loadRecord("/api/farm/v1/farms", farmId)));
    farms.forEach((farm, index) => farm && farmNames.set(farmIds[index], String(farm.name)));
  }
  const now = new Date();
  const expectedByCrop = new Map<string, number>();
  for (const season of crops?.rows ?? []) {
    expectedByCrop.set(season.cropCode, (expectedByCrop.get(season.cropCode) ?? 0) + (season.expectedYieldKg ?? 0));
  }
  const today = new Date().toLocaleDateString("en-KE", { weekday: "long", day: "numeric", month: "long" });

  return (
    <>
      <PageHeader eyebrow={today} title={`${ROLE_LABELS[role]} overview`} subtitle={SUBTITLE[role]} />

      <section className="grid grid-cols-2 gap-3 md:grid-cols-[repeat(auto-fit,minmax(190px,1fr))]">
        {myTaskCount !== null ? (
          <Link href="/my-work" className="block">
            <Stat label="My open tasks" value={String(myTaskCount)} hint="Assigned to you" icon={<Icon name="tasks" className="h-4 w-4" />} />
          </Link>
        ) : null}
        {headlines.map((item, index) => (
          <Link key={item.label} href={item.href} className="block">
            <Stat
              label={item.label}
              value={counts[index]?.toLocaleString() ?? "—"}
              hint={item.hint}
              icon={<Icon name={item.icon} className="h-4 w-4" />}
            />
          </Link>
        ))}
      </section>

      {crops && !crops.failed && shownCrops.length > 0 ? (
        <section className="mt-6">
          <Card
            title="Crops in the ground"
            description={`${crops.totalElements} active season${crops.totalElements === 1 ? "" : "s"}, soonest harvest first. Progress is calendar time against the expected harvest date.`}
            actions={
              <Link href="/season/seasons?status=ACTIVE" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[var(--color-accent)] hover:underline">
                All seasons <Icon name="arrow" className="h-4 w-4" />
              </Link>
            }
            flush
          >
            <ul className="divide-y divide-[var(--color-line)]">
              {shownCrops.map((season) => {
                const progress = progressOf(season, now);
                return (
                  <li key={season.id}>
                    <Link
                      href={`/farm/farms/${season.farmId}`}
                      className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2.5 hover:bg-[var(--color-raised)]"
                    >
                      <span className="min-w-[180px] flex-1">
                        <span className="block text-sm font-semibold leading-snug">
                          {crop(season.cropCode)}
                          {season.variety ? <span className="font-normal text-[var(--color-muted)]"> · {season.variety}</span> : null}
                        </span>
                        <span className="block text-sm text-[var(--color-muted)]">
                          {farmNames.get(season.farmId) ?? "Farm"}
                        </span>
                      </span>
                      <span className="w-28">{season.currentStage ? <Badge value={season.currentStage} /> : null}</span>
                      <span className="w-48">
                        {progress ? (
                          <>
                            <Meter value={progress.fraction} tone={progress.tone} />
                            <span className={`mt-1 block text-xs ${progress.tone === "danger" ? "font-semibold text-[var(--color-danger)]" : "text-[var(--color-muted)]"}`}>
                              {progress.label}
                            </span>
                          </>
                        ) : null}
                      </span>
                      <span className="w-24 text-right text-sm font-semibold tabular-nums">{kg(season.expectedYieldKg)}</span>
                    </Link>
                  </li>
                );
              })}
            </ul>
            {expectedByCrop.size > 1 && crops && crops.totalElements <= crops.rows.length ? (
              <p className="border-t border-[var(--color-line)] px-4 py-2 text-sm text-[var(--color-muted)]">
                Expected across active seasons:{" "}
                {[...expectedByCrop.entries()]
                  .sort((x, y) => y[1] - x[1])
                  .map(([code, total]) => `${crop(code)} ${kg(total)}`)
                  .join(" · ")}
              </p>
            ) : null}
          </Card>
        </section>
      ) : null}

      {inProgress.length > 0 ? (
        <section className="mt-6">
          <Card
            title="Work in progress"
            description="Tasks workers have started or been given. Times are the ones the platform recorded."
            actions={
              <Link href="/live" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[var(--color-accent)] hover:underline">
                Open live board <Icon name="arrow" className="h-4 w-4" />
              </Link>
            }
            flush
          >
            <ul className="divide-y divide-[var(--color-line)]">
              {inProgress.map((card) => (
                <li key={card.assignment.id}>
                  <Link
                    href={`/work/${card.assignment.id}`}
                    className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2.5 hover:bg-[var(--color-raised)]"
                  >
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-accent-soft)] text-sm font-bold text-[var(--color-accent-deep)]">
                      {(card.worker?.fullName ?? "?").charAt(0)}
                    </span>
                    <span className="min-w-[200px] flex-1">
                      <span className="block text-sm font-semibold leading-snug">{card.order?.title ?? "Task"}</span>
                      <span className="block text-sm text-[var(--color-muted)]">
                        {card.worker?.fullName ?? "Unassigned"}
                        {card.order?.taskCode ? ` · ${card.order.taskCode}` : ""}
                      </span>
                    </span>
                    <span className="w-24 text-sm tabular-nums text-[var(--color-muted)]">
                      {card.assignment.startedAt ? `Since ${clockTime(card.assignment.startedAt)}` : "Not started"}
                    </span>
                    <span className="w-20 text-right text-sm font-semibold tabular-nums">
                      {formatDuration(card.elapsedMinutes)}
                    </span>
                    <span className="w-32 text-right">
                      <Badge value={card.assignment.status} />
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </Card>
        </section>
      ) : null}

      {visibleGroups.length > 1 ? (
        <section className="mt-6">
          <h2 className="mb-3 text-[15px] font-semibold">Workspaces</h2>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {visibleGroups.map((group) => (
              <div
                key={group.slug}
                className="overflow-hidden rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] shadow-[var(--shadow-card)]"
              >
                <div className="flex items-center gap-3 border-b border-[var(--color-line)] px-4 py-2.5">
                  <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
                    <Icon name={group.icon as IconName} className="h-4 w-4" />
                  </span>
                  <div className="flex-1">
                    <h3 className="text-[15px] font-semibold leading-tight">{group.label}</h3>
                    <p className="text-xs text-[var(--color-muted)]">
                      {group.services.length} service{group.services.length === 1 ? "" : "s"}
                    </p>
                  </div>
                </div>
                <ul className="py-1">
                  {group.services.map((service) => (
                    <li key={service.slug}>
                      <Link
                        href={`/${service.slug}`}
                        className="flex items-center justify-between gap-3 px-4 py-1.5 text-sm hover:bg-[var(--color-raised)]"
                      >
                        <span className="truncate font-medium">{service.label}</span>
                        <span className="shrink-0 rounded-full bg-[var(--color-raised)] px-2 py-0.5 text-xs font-semibold uppercase tracking-[0.04em] text-[var(--color-muted)]">
                          {service.access[role] ?? "full"}
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </section>
      ) : null}
    </>
  );
}

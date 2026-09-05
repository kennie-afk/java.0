import { Card, PageHeader, Stat } from "@/components/ui";
import { api, ApiError, type PageResponse } from "@/lib/api";
import { readToken } from "@/lib/session";
import type { FraudCase, Farm, SupplyListing } from "@/lib/types";

async function safeCount(path: string, token: string | null): Promise<number | null> {
  try {
    const page = await api.get<PageResponse<unknown>>(path, token);
    return page.totalElements;
  } catch (error) {
    if (error instanceof ApiError) {
      return null;
    }
    return null;
  }
}

async function safeList<T>(path: string, token: string | null): Promise<T[]> {
  try {
    const page = await api.get<PageResponse<T>>(path, token);
    return page.content;
  } catch {
    return [];
  }
}

export default async function OverviewPage() {
  const token = await readToken();

  const [farms, seasons, cases, listings] = await Promise.all([
    safeCount("/api/farm/v1/farms?size=1", token),
    safeCount("/api/season/v1/seasons?size=1", token),
    safeCount("/api/fraud/v1/fraud-cases?size=1", token),
    safeCount("/api/marketplace/v1/supply-listings?size=1", token)
  ]);

  const [recentFarms, openCases, activeListings] = await Promise.all([
    safeList<Farm>("/api/farm/v1/farms?size=5", token),
    safeList<FraudCase>("/api/fraud/v1/fraud-cases?size=5", token),
    safeList<SupplyListing>("/api/marketplace/v1/supply-listings?size=5", token)
  ]);

  const unreachable = farms === null && seasons === null && cases === null;

  return (
    <>
      <PageHeader
        title="Overview"
        subtitle="Platform activity across farms, seasons, workforce integrity and trade."
      />

      {unreachable ? (
        <Card>
          <p className="text-sm text-[var(--color-muted)]">
            The API gateway is not reachable. Start the platform with{" "}
            <code className="rounded bg-[#eef1ef] px-1.5 py-0.5 text-xs">docker compose up</code>{" "}
            and sign in to populate this view.
          </p>
        </Card>
      ) : null}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Farms" value={farms?.toLocaleString() ?? "—"} />
        <Stat label="Seasons" value={seasons?.toLocaleString() ?? "—"} />
        <Stat label="Fraud cases" value={cases?.toLocaleString() ?? "—"} hint="Open and under review" />
        <Stat label="Listings" value={listings?.toLocaleString() ?? "—"} hint="Produce on the marketplace" />
      </section>

      <section className="mt-8 grid gap-4 lg:grid-cols-3">
        <Card>
          <h2 className="mb-3 text-sm font-semibold">Recent farms</h2>
          <ul className="space-y-2">
            {recentFarms.length === 0 ? (
              <li className="text-sm text-[var(--color-muted)]">No farms yet.</li>
            ) : (
              recentFarms.map((farm) => (
                <li key={farm.id} className="flex justify-between text-sm">
                  <span>{farm.name}</span>
                  <span className="text-[var(--color-muted)]">{farm.county ?? "—"}</span>
                </li>
              ))
            )}
          </ul>
        </Card>

        <Card>
          <h2 className="mb-3 text-sm font-semibold">Fraud cases</h2>
          <ul className="space-y-2">
            {openCases.length === 0 ? (
              <li className="text-sm text-[var(--color-muted)]">Nothing flagged.</li>
            ) : (
              openCases.map((item) => (
                <li key={item.id} className="flex justify-between text-sm">
                  <span>{item.caseNumber}</span>
                  <span className="text-[var(--color-muted)]">{item.typology}</span>
                </li>
              ))
            )}
          </ul>
        </Card>

        <Card>
          <h2 className="mb-3 text-sm font-semibold">Active listings</h2>
          <ul className="space-y-2">
            {activeListings.length === 0 ? (
              <li className="text-sm text-[var(--color-muted)]">No listings yet.</li>
            ) : (
              activeListings.map((listing) => (
                <li key={listing.id} className="flex justify-between text-sm">
                  <span>{listing.commodityCode}</span>
                  <span className="tabular-nums text-[var(--color-muted)]">
                    {listing.quantity} {listing.unit}
                  </span>
                </li>
              ))
            )}
          </ul>
        </Card>
      </section>
    </>
  );
}

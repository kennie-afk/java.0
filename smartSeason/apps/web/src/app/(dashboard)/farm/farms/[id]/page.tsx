import Link from "next/link";
import {
  Badge,
  Card,
  EmptyState,
  KeyValue,
  Meter,
  Notice,
  PageHeader,
  Stat,
  Table,
  buttonClass,
  rowClass,
  secondaryButtonClass
} from "@/components/ui";
import { enabledServices } from "@/lib/deployment";
import { loadCollection } from "@/lib/load";
import { display, loadRecord, type Record_ } from "@/lib/record";
import { canWrite } from "@/lib/roles";
import { crop, daysBetween, kg, progressOf, type Season } from "@/lib/season";
import { readRoles } from "@/lib/session";

interface Plot extends Record_ {
  id: string;
  name: string;
  areaHa: number | null;
  irrigated: boolean | null;
  currentCrop: string | null;
  status: string;
}

export default async function FarmPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const enabled = enabledServices();
  const seasonsRunning = !enabled || enabled.includes("season");

  const [farm, plots, seasons, roles] = await Promise.all([
    loadRecord("/api/farm/v1/farms", id),
    loadCollection<Plot>(`/api/farm/v1/plots?farmId=${encodeURIComponent(id)}`),
    seasonsRunning
      ? loadCollection<Season>(`/api/season/v1/seasons?farmId=${encodeURIComponent(id)}`)
      : Promise.resolve(null),
    readRoles()
  ]);

  if (!farm) {
    return (
      <>
        <PageHeader title="Farm" subtitle="Farms, plots and what is growing on them" />
        <EmptyState
          message="This farm could not be loaded."
          detail="It may have been deleted, or the farm service is not reachable."
          action={
            <Link href="/farm/farms" className={secondaryButtonClass}>
              Back to farms
            </Link>
          }
        />
      </>
    );
  }

  const today = new Date();
  const seasonRows = seasons?.rows ?? [];
  // The newest season per plot is the one that is "on" the plot now.
  const latest = new Map<string, Season>();
  for (const season of [...seasonRows].sort((x, y) => (y.startDate ?? "").localeCompare(x.startDate ?? ""))) {
    if (!latest.has(season.plotId)) latest.set(season.plotId, season);
  }
  const active = seasonRows.filter((season) => season.status === "ACTIVE");
  const expectedYield = active.reduce((sum, season) => sum + (season.expectedYieldKg ?? 0), 0);
  const nextHarvest = active
    .filter((season) => season.expectedHarvestDate)
    .sort((x, y) => (x.expectedHarvestDate ?? "").localeCompare(y.expectedHarvestDate ?? ""))[0];
  const nextHarvestDays = nextHarvest?.expectedHarvestDate
    ? daysBetween(today, new Date(nextHarvest.expectedHarvestDate))
    : null;
  const plotArea = plots.rows.reduce((sum, plot) => sum + (plot.areaHa ?? 0), 0);
  const farmArea = typeof farm.totalAreaHa === "number" ? farm.totalAreaHa : null;
  const unplanted = plots.rows.filter((plot) => !latest.has(plot.id) || latest.get(plot.id)?.status !== "ACTIVE");

  const place = [farm.subCounty, farm.county].filter(Boolean).join(", ");
  const mayEdit = canWrite(roles, "farm");

  return (
    <>
      <PageHeader
        eyebrow={place || undefined}
        title={String(farm.name ?? "Farm")}
        subtitle={farm.registrationNo ? `Registration ${String(farm.registrationNo)}` : "Plots, seasons and expected yield"}
        actions={
          <>
            {mayEdit ? (
              <Link href={`/farm/farms/${id}/edit`} className={buttonClass}>
                Edit
              </Link>
            ) : null}
            <Link href="/farm/farms" className={secondaryButtonClass}>
              All farms
            </Link>
          </>
        }
      />

      <section className="grid grid-cols-2 gap-3 md:grid-cols-[repeat(auto-fit,minmax(170px,1fr))]">
        <Stat
          label="Area"
          value={farmArea === null ? "—" : `${farmArea.toLocaleString()} ha`}
          hint={plots.rows.length > 0 ? `${plotArea.toLocaleString()} ha in plots` : undefined}
        />
        <Stat label="Plots" value={String(plots.totalElements)} hint={unplanted.length > 0 ? `${unplanted.length} with no active season` : "All planted"} />
        <Stat label="Active seasons" value={seasons ? String(active.length) : "—"} />
        <Stat label="Expected yield" value={seasons ? kg(expectedYield) : "—"} hint="Active seasons" />
        <Stat
          label="Next harvest"
          value={nextHarvest?.expectedHarvestDate ?? "—"}
          hint={
            nextHarvestDays === null
              ? undefined
              : nextHarvestDays < 0
                ? `${-nextHarvestDays} days overdue`
                : `In ${nextHarvestDays} days`
          }
          tone={nextHarvestDays !== null && nextHarvestDays < 0 ? "danger" : undefined}
        />
      </section>

      {farmArea !== null && plotArea > farmArea + 0.0001 ? (
        <div className="mt-4">
          <Notice tone="warn">
            Plots add up to {plotArea.toLocaleString()} ha, more than the {farmArea.toLocaleString()} ha recorded for
            the farm. One of the two is wrong.
          </Notice>
        </div>
      ) : null}

      <section className="mt-6">
        <Card
          title="Plots and what is growing"
          description="Each plot with its current season. Progress is calendar time against the expected harvest date."
          actions={
            mayEdit ? (
              <Link href="/farm/plots/new" className="text-sm font-semibold text-[var(--color-accent)] hover:underline">
                Add a plot
              </Link>
            ) : undefined
          }
          flush
        >
          {plots.failed ? (
            <div className="p-4">
              <EmptyState message="Plots could not be loaded." detail="The farm service is not reachable." />
            </div>
          ) : plots.rows.length === 0 ? (
            <div className="p-4">
              <EmptyState message="No plots on this farm yet." detail="Add the first plot to start tracking seasons." />
            </div>
          ) : (
            <Table
              head={[
                { key: "plot", label: "Plot" },
                { key: "crop", label: "Crop" },
                { key: "area", label: "Area", numeric: true },
                { key: "stage", label: "Stage" },
                { key: "progress", label: "Progress" },
                { key: "yield", label: "Expected yield", numeric: true },
                { key: "perha", label: "Per ha", numeric: true }
              ]}
            >
              {plots.rows.map((plot) => {
                const season = latest.get(plot.id);
                const progress = season ? progressOf(season, today) : null;
                const perHa =
                  season?.expectedYieldKg && plot.areaHa ? season.expectedYieldKg / plot.areaHa : null;
                return (
                  <tr key={plot.id} className={rowClass}>
                    <td className="px-4 py-2">
                      <Link
                        href={`/farm/plots/${plot.id}`}
                        className="font-medium text-[var(--color-ink)] underline-offset-2 hover:underline"
                      >
                        {plot.name}
                      </Link>
                      <span className="block text-xs text-[var(--color-muted)]">
                        {plot.irrigated ? "Irrigated" : "Rain-fed"}
                      </span>
                    </td>
                    <td className="px-4 py-2">
                      {season ? (
                        <>
                          <Link
                            href={`/season/seasons/${season.id}`}
                            className="font-medium underline-offset-2 hover:underline"
                          >
                            {crop(season.cropCode)}
                          </Link>
                          {season.variety ? (
                            <span className="block text-xs text-[var(--color-muted)]">{season.variety}</span>
                          ) : null}
                        </>
                      ) : plot.currentCrop ? (
                        <span className="text-[var(--color-muted)]">{display(plot.currentCrop, "string")}</span>
                      ) : (
                        <span className="text-[var(--color-muted)]">Fallow</span>
                      )}
                    </td>
                    <td className="px-4 py-2 text-right tabular-nums">{plot.areaHa ?? "—"} ha</td>
                    <td className="px-4 py-2">
                      {season?.currentStage ? <Badge value={season.currentStage} dot={false} /> : <span className="text-[var(--color-muted)]">—</span>}
                    </td>
                    <td className="min-w-[180px] px-4 py-2">
                      {progress ? (
                        <>
                          <Meter value={progress.fraction} tone={progress.tone} />
                          <span
                            className={`mt-1 block text-xs ${
                              progress.tone === "danger" ? "font-semibold text-[var(--color-danger)]" : "text-[var(--color-muted)]"
                            }`}
                          >
                            {progress.label}
                          </span>
                        </>
                      ) : (
                        <span className="text-[var(--color-muted)]">{seasons ? "No season" : "—"}</span>
                      )}
                    </td>
                    <td className="px-4 py-2 text-right tabular-nums">{kg(season?.expectedYieldKg)}</td>
                    <td className="px-4 py-2 text-right tabular-nums text-[var(--color-muted)]">
                      {perHa === null ? "—" : `${Math.round(perHa).toLocaleString()} kg`}
                    </td>
                  </tr>
                );
              })}
            </Table>
          )}
        </Card>
        {plots.totalElements > plots.rows.length ? (
          <p className="mt-2 text-sm text-[var(--color-muted)]">
            Showing {plots.rows.length} of {plots.totalElements} plots.{" "}
            <Link
              href={`/farm/plots?farmId=${id}`}
              className="font-semibold text-[var(--color-accent)] hover:underline"
            >
              See all plots
            </Link>
          </p>
        ) : null}
      </section>

      {seasons?.failed ? (
        <div className="mt-4">
          <Notice tone="warn">Seasons could not be loaded, so progress and yield are not shown.</Notice>
        </div>
      ) : null}

      <section className="mt-6">
        <Card title="Farm details">
          <KeyValue
            items={[
              ["County", display(farm.county, "string")],
              ["Sub-county", display(farm.subCounty, "string")],
              ["Ward", display(farm.ward, "string")],
              [
                "Coordinates",
                farm.latitude != null && farm.longitude != null
                  ? `${String(farm.latitude)}, ${String(farm.longitude)}`
                  : "—"
              ],
              ["Status", display(farm.status, "string")],
              ["Registered", display(farm.createdAt, "ts")]
            ]}
          />
        </Card>
      </section>
    </>
  );
}

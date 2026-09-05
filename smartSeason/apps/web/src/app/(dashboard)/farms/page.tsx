import { Badge, EmptyState, PageHeader } from "@/components/ui";
import { EntityForm } from "@/components/entity-form";
import { createFarm } from "@/lib/actions";
import { api, type PageResponse } from "@/lib/api";
import { readToken } from "@/lib/session";
import type { Farm } from "@/lib/types";

export default async function FarmsPage() {
  const token = await readToken();

  let farms: Farm[] = [];
  let failed = false;
  try {
    const page = await api.get<PageResponse<Farm>>("/api/farm/v1/farms?size=50", token);
    farms = page.content;
  } catch {
    failed = true;
  }

  return (
    <>
      <PageHeader title="Farms" subtitle="Registered farms, plots and cooperative membership." />
      <EntityForm
        action={createFarm}
        title="Add a farm"
        submitLabel="Create farm"
        fields={[
          { name: "name", label: "Name", required: true, placeholder: "Njoro Home Farm" },
          { name: "county", label: "County", placeholder: "Nakuru" },
          { name: "subCounty", label: "Sub-county", placeholder: "Njoro" },
          { name: "totalAreaHa", label: "Area (ha)", type: "number", step: "0.1" },
          { name: "latitude", label: "Latitude", type: "number", step: "0.000001" },
          { name: "longitude", label: "Longitude", type: "number", step: "0.000001" }
        ]}
      />
      {failed ? (
        <EmptyState message="farm-service is not reachable." />
      ) : farms.length === 0 ? (
        <EmptyState message="No farms registered yet." />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)]">
          <table className="w-full text-sm">
            <thead className="border-b border-[var(--color-line)] text-left text-xs uppercase tracking-wide text-[var(--color-muted)]">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">County</th>
                <th className="px-4 py-3 font-medium">Area (ha)</th>
                <th className="px-4 py-3 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {farms.map((farm) => (
                <tr key={farm.id} className="border-b border-[var(--color-line)] last:border-0">
                  <td className="px-4 py-3">{farm.name}</td>
                  <td className="px-4 py-3 text-[var(--color-muted)]">{farm.county ?? "—"}</td>
                  <td className="px-4 py-3 tabular-nums">{farm.totalAreaHa ?? "—"}</td>
                  <td className="px-4 py-3"><Badge value={farm.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

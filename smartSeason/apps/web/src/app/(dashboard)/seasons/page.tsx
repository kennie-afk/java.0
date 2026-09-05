import { Badge, PageHeader } from "@/components/ui";
import { ResourceTable } from "@/components/resource-table";
import { api, type PageResponse } from "@/lib/api";
import { readToken } from "@/lib/session";
import type { Season } from "@/lib/types";

export default async function SeasonsPage() {
  const token = await readToken();

  let rows: Season[] = [];
  let failed = false;
  try {
    const page = await api.get<PageResponse<Season>>("/api/season/v1/seasons?size=50", token);
    rows = page.content;
  } catch {
    failed = true;
  }

  return (
    <>
      <PageHeader title="Seasons" subtitle="Crop cycles per plot, from planting through harvest." />
      <ResourceTable
        rows={rows}
        failed={failed}
        failureMessage="season-service is not reachable."
        emptyMessage="No seasons started yet."
        columns={[
          { header: "Crop", render: (row) => row.cropCode },
          { header: "Variety", render: (row) => row.variety ?? "—" },
          { header: "Started", render: (row) => row.startDate },
          { header: "Stage", render: (row) => row.currentStage ?? "—" },
          {
            header: "Expected yield",
            numeric: true,
            render: (row) => (row.expectedYieldKg ? `${row.expectedYieldKg} kg` : "—")
          },
          { header: "Status", render: (row) => <Badge value={row.status} /> }
        ]}
      />
    </>
  );
}

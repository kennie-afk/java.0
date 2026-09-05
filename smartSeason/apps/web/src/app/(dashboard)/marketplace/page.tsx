import { Badge, PageHeader } from "@/components/ui";
import { ResourceTable } from "@/components/resource-table";
import { api, type PageResponse } from "@/lib/api";
import { readToken } from "@/lib/session";
import type { SupplyListing } from "@/lib/types";

export default async function MarketplacePage() {
  const token = await readToken();

  let rows: SupplyListing[] = [];
  let failed = false;
  try {
    const page = await api.get<PageResponse<SupplyListing>>(
      "/api/marketplace/v1/supply-listings?size=50",
      token
    );
    rows = page.content;
  } catch {
    failed = true;
  }

  return (
    <>
      <PageHeader title="Marketplace" subtitle="Produce offered for sale by farms and cooperatives." />
      <ResourceTable
        rows={rows}
        failed={failed}
        failureMessage="marketplace-service is not reachable."
        emptyMessage="No listings published yet."
        columns={[
          { header: "Commodity", render: (row) => row.commodityCode },
          { header: "Grade", render: (row) => row.grade ?? "—" },
          { header: "Quantity", numeric: true, render: (row) => `${row.quantity} ${row.unit}` },
          {
            header: "Ask",
            numeric: true,
            render: (row) => `${row.currency} ${row.askPrice.toLocaleString()}`
          },
          { header: "County", render: (row) => row.county ?? "—" },
          { header: "Status", render: (row) => <Badge value={row.status} /> }
        ]}
      />
    </>
  );
}

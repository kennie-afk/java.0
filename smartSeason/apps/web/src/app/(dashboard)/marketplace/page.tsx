import { Badge, PageHeader } from "@/components/ui";
import { EntityForm } from "@/components/entity-form";
import { createListing } from "@/lib/actions";
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
      <EntityForm
        action={createListing}
        title="Publish a listing"
        submitLabel="Publish"
        fields={[
          { name: "sellerOrgId", label: "Seller organisation id", required: true },
          { name: "commodityCode", label: "Commodity", required: true, placeholder: "MAIZE" },
          { name: "variety", label: "Variety" },
          {
            name: "grade", label: "Grade",
            options: [
              { value: "GRADE_1", label: "Grade 1" },
              { value: "GRADE_2", label: "Grade 2" },
              { value: "GRADE_3", label: "Grade 3" }
            ]
          },
          { name: "quantity", label: "Quantity", type: "number", step: "0.1", required: true },
          { name: "unit", label: "Unit", placeholder: "kg" },
          { name: "askPrice", label: "Ask price (KES)", type: "number", step: "0.01", required: true },
          { name: "county", label: "County", placeholder: "Nakuru" }
        ]}
      />
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

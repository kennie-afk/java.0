import { Badge, PageHeader } from "@/components/ui";
import { EntityForm } from "@/components/entity-form";
import { createWorker } from "@/lib/actions";
import { ResourceTable } from "@/components/resource-table";
import { api, type PageResponse } from "@/lib/api";
import { readToken } from "@/lib/session";
import type { Worker } from "@/lib/types";

function riskBand(score: number): string {
  if (score >= 75) return "CRITICAL";
  if (score >= 50) return "HIGH";
  if (score >= 25) return "MEDIUM";
  return "LOW";
}

export default async function WorkforcePage() {
  const token = await readToken();

  let rows: Worker[] = [];
  let failed = false;
  try {
    const page = await api.get<PageResponse<Worker>>("/api/workforce/v1/workers?size=50", token);
    rows = page.content;
  } catch {
    failed = true;
  }

  return (
    <>
      <PageHeader title="Workforce" subtitle="Workers, contracts and integrity risk scores." />
      <EntityForm
        action={createWorker}
        title="Onboard a worker"
        submitLabel="Create worker"
        fields={[
          { name: "fullName", label: "Full name", required: true, placeholder: "Amina Wanjiru" },
          { name: "phone", label: "Phone", type: "tel", placeholder: "0712345678" },
          { name: "nationalId", label: "National ID" },
          { name: "farmId", label: "Farm id" }
        ]}
      />
      <ResourceTable
        rows={rows}
        failed={failed}
        failureMessage="workforce-service is not reachable."
        emptyMessage="No workers onboarded yet."
        columns={[
          { header: "Name", render: (row) => row.fullName },
          { header: "Phone", render: (row) => row.phone ?? "—" },
          { header: "Risk", numeric: true, render: (row) => row.riskScore },
          { header: "Band", render: (row) => <Badge value={riskBand(row.riskScore)} /> },
          { header: "Status", render: (row) => <Badge value={row.status} /> }
        ]}
      />
    </>
  );
}

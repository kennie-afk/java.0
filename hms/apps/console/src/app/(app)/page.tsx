"use client";

import Link from "next/link";
import { useFetch, type Slice } from "@/lib/api";
import { addDays, kes, today } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Card, Grid, Page, Stat } from "@/components/ui";

type Overview = {
  outpatient: { visits: number; under5: number; topDiagnoses: { key: string; count: number }[] };
  finance: { invoiced: number; collected: number; outstanding: number };
  inpatient: { bedsTotal: number; bedsOccupied: number; occupancyPercent: number; admissions: number };
  laboratory: { orders: number; unacknowledgedCritical: number; averageTurnaroundMinutes?: number };
};

export default function Overview() {
  const { can, facilityId, facility } = useSession();
  const d = today();
  const report = useFetch<Overview>(can("reports:read") ? `/v1/reports/overview?facilityId=${facilityId}&from=${addDays(d, -6)}&to=${d}` : null);
  const queue = useFetch<unknown[]>(can("scheduling:read") ? `/v1/scheduling/queue?facilityId=${facilityId}` : null);
  const pharmacy = useFetch<Slice<unknown>>(can("pharmacy:read") ? `/v1/pharmacy/queue?facilityId=${facilityId}&limit=100` : null);
  const critical = useFetch<unknown[]>(can("lab:read") ? `/v1/lab/critical?facilityId=${facilityId}` : null);
  const claims = useFetch<{ byStatus: Record<string, number> }>(can("claims:read") ? `/v1/claims/summary?facilityId=${facilityId}` : null);
  const r = report.data;
  return (
    <Page title={facility.name} sub="Today at a glance. Reports cover the last 7 days.">
      <Grid cols={4}>
        {queue.data && <Stat label="Waiting now" value={queue.data.length} />}
        {pharmacy.data && <Stat label="Prescriptions waiting" value={pharmacy.data.data.length + (pharmacy.data.nextCursor ? "+" : "")} />}
        {critical.data && <Stat label="Unacknowledged critical results" value={critical.data.length} tone={critical.data.length ? "danger" : undefined} />}
        {claims.data && <Stat label="Claims needing attention" value={claims.data.byStatus.NEEDS_ATTENTION ?? 0} tone={(claims.data.byStatus.NEEDS_ATTENTION ?? 0) > 0 ? "warn" : undefined} />}
      </Grid>
      {r && (
        <Grid cols={4}>
          <Stat label="Outpatient visits" value={`${r.outpatient.visits} (${r.outpatient.under5} under 5)`} />
          <Stat label="Collected" value={kes(r.finance.collected)} />
          <Stat label="Outstanding" value={kes(r.finance.outstanding)} />
          <Stat label="Bed occupancy" value={`${r.inpatient.bedsOccupied}/${r.inpatient.bedsTotal} (${r.inpatient.occupancyPercent}%)`} />
        </Grid>
      )}
      {r && r.outpatient.topDiagnoses.length > 0 && (
        <Card title="Top diagnoses (7 days)">
          <ul className="space-y-1 text-xs">
            {r.outpatient.topDiagnoses.map((t) => <li key={t.key} className="flex justify-between"><span>{t.key}</span><b className="tabular-nums">{t.count}</b></li>)}
          </ul>
          <div className="pt-2 text-xs"><Link href="/reports" className="text-accent hover:underline">All reports</Link></div>
        </Card>
      )}
    </Page>
  );
}

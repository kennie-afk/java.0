import Link from "next/link";
import { Card, EmptyState, Notice, PageHeader, Stat, Table, rowClass } from "@/components/ui";
import { litres, loadDairySummary, loadFarms } from "@/lib/dairy";

export const dynamic = "force-dynamic";

const cell = "px-4 py-2.5";

export default async function DairyPage({
  searchParams
}: {
  searchParams: Promise<{ farm?: string; from?: string; to?: string }>;
}) {
  const params = await searchParams;
  const farms = await loadFarms();

  if (farms === null) {
    return (
      <>
        <PageHeader title="Dairy" />
        <Notice tone="danger">The farm service could not be reached. Try again in a moment.</Notice>
      </>
    );
  }
  if (farms.length === 0) {
    return (
      <>
        <PageHeader title="Dairy" />
        <EmptyState message="No farms yet." detail="Add a farm first, then record its herd, milk and deliveries." />
      </>
    );
  }

  const farm = farms.find((f) => f.id === params.farm) ?? farms[0];
  const summary = await loadDairySummary(farm.id, params.from, params.to);
  const affected = new Set((summary?.milkedDuringWithdrawal ?? []).map((c) => c.cowId)).size;

  return (
    <>
      <PageHeader
        title="Dairy"
        subtitle="Milk recorded from the herd, milk delivered to buyers, and animals whose milk must be held back."
      />

      {farms.length > 1 ? (
        <nav className="mb-4 flex flex-wrap gap-2 text-sm" aria-label="Farm">
          {farms.map((f) => (
            <Link
              key={f.id}
              href={`/dairy?farm=${f.id}`}
              className={`rounded-lg border px-3 py-1.5 ${
                f.id === farm.id
                  ? "border-[var(--color-accent)] bg-[var(--color-accent-soft)] font-semibold text-[var(--color-accent-deep)]"
                  : "border-[var(--color-line)] text-[var(--color-muted)]"
              }`}
            >
              {f.name}
            </Link>
          ))}
        </nav>
      ) : null}

      {summary === null ? (
        <Notice tone="danger">The dairy summary could not be loaded.</Notice>
      ) : (
        <div className="space-y-4">
          <p className="text-xs text-[var(--color-muted)]">
            {farm.name}, {summary.from} to {summary.to}
          </p>

          {summary.milkedDuringWithdrawal.length > 0 ? (
            <Notice tone="danger">
              Milk was recorded from {affected} {affected === 1 ? "cow" : "cows"} during a medicine withdrawal
              period. That milk should not have been sold. Details below.
            </Notice>
          ) : null}

          <div className="grid grid-cols-[repeat(auto-fit,minmax(190px,1fr))] gap-3">
            <Stat label="Recorded" value={litres(summary.litresRecorded)} hint={`${summary.milkingCows} cows milking`} />
            <Stat label="Delivered" value={litres(summary.litresDelivered)} />
            <Stat
              label="Rejected"
              value={litres(summary.litresRejected)}
              hint={`${summary.rejectionRatePct}% of delivered`}
              tone={summary.rejectionRatePct > 5 ? "warn" : undefined}
            />
            <Stat
              label="Average fat"
              value={summary.weightedFatPct === null ? "Not recorded" : `${summary.weightedFatPct}%`}
              hint="weighted by litres"
            />
            <Stat
              label="Delivered value"
              value={summary.deliveredValue.toLocaleString("en-KE", { maximumFractionDigits: 0 })}
              hint={
                summary.deliveriesWithoutPrice > 0
                  ? `${summary.deliveriesWithoutPrice} deliveries have no price and are not counted`
                  : "from the prices you entered"
              }
              tone={summary.deliveriesWithoutPrice > 0 ? "warn" : undefined}
            />
          </div>

          <Card title="Cows under withdrawal" description="Do not deliver milk from these cows until the date shown." flush>
            {summary.underWithdrawal.length === 0 ? (
              <p className="px-4 py-3 text-sm text-[var(--color-muted)]">None.</p>
            ) : (
              <Table head={[{ key: "t", label: "Tag" }, { key: "m", label: "Medicine" }, { key: "e", label: "Last day" }]}>
                {summary.underWithdrawal.map((w) => (
                  <tr key={w.cowId} className={rowClass}>
                    <td className={`${cell} font-medium`}>{w.tagNo}</td>
                    <td className={cell}>{w.medicine ?? "Not recorded"}</td>
                    <td className={cell}>{w.endsOn}</td>
                  </tr>
                ))}
              </Table>
            )}
          </Card>

          {summary.milkedDuringWithdrawal.length > 0 ? (
            <Card title="Milk recorded inside a withdrawal period" flush>
              <Table
                head={[
                  { key: "t", label: "Tag" }, { key: "d", label: "Day" }, { key: "l", label: "Litres", numeric: true },
                  { key: "m", label: "Medicine" }, { key: "e", label: "Withdrawal ends" }
                ]}
              >
                {summary.milkedDuringWithdrawal.map((c, i) => (
                  <tr key={`${c.cowId}-${c.day}-${i}`} className={rowClass}>
                    <td className={`${cell} font-medium`}>{c.tagNo}</td>
                    <td className={cell}>{c.day}</td>
                    <td className={`${cell} text-right tabular-nums`}>{litres(c.litres)}</td>
                    <td className={cell}>{c.medicine ?? "Not recorded"}</td>
                    <td className={cell}>{c.withdrawalEndsOn}</td>
                  </tr>
                ))}
              </Table>
            </Card>
          ) : null}

          <Card title="By cow" description="Litres recorded in the period." flush>
            {summary.cows.length === 0 ? (
              <p className="px-4 py-3 text-sm text-[var(--color-muted)]">No milk recorded in this period.</p>
            ) : (
              <Table
                head={[
                  { key: "t", label: "Tag" }, { key: "n", label: "Name" }, { key: "l", label: "Litres", numeric: true },
                  { key: "d", label: "Days", numeric: true }, { key: "a", label: "Per day", numeric: true }
                ]}
              >
                {summary.cows.map((c) => (
                  <tr key={c.cowId} className={rowClass}>
                    <td className={`${cell} font-medium`}>{c.tagNo}</td>
                    <td className={cell}>{c.name ?? ""}</td>
                    <td className={`${cell} text-right tabular-nums`}>{litres(c.litres)}</td>
                    <td className={`${cell} text-right tabular-nums`}>{c.daysRecorded}</td>
                    <td className={`${cell} text-right tabular-nums`}>{litres(c.litresPerRecordedDay)}</td>
                  </tr>
                ))}
              </Table>
            )}
          </Card>

          <Card title="Expected calvings" description="From the latest breeding record with an expected date, for cows that have not calved since." flush>
            {summary.expectedCalvings.length === 0 ? (
              <p className="px-4 py-3 text-sm text-[var(--color-muted)]">None recorded.</p>
            ) : (
              <Table head={[{ key: "t", label: "Tag" }, { key: "e", label: "Expected" }]}>
                {summary.expectedCalvings.map((c) => (
                  <tr key={c.cowId} className={rowClass}>
                    <td className={`${cell} font-medium`}>{c.tagNo}</td>
                    <td className={cell}>{c.expectedOn}</td>
                  </tr>
                ))}
              </Table>
            )}
          </Card>

          <p className="text-xs text-[var(--color-muted)]">
            Record cows, milk, deliveries, treatments and breeding under Farm in the menu.
          </p>
        </div>
      )}
    </>
  );
}

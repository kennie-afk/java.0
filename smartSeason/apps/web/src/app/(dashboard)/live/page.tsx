import Link from "next/link";
import { Badge, EmptyState, PageHeader, Stat, Table, rowClass } from "@/components/ui";
import { clockTime, formatDuration, loadTaskBoard, STALE_MINUTES, type TaskCard } from "@/lib/tasks";

export const dynamic = "force-dynamic";

/** Flags a task worth a second look, and says why in plain words. */
function concern(card: TaskCard): string | null {
  if (card.overrun !== null && card.overrun > 1.5) {
    return `${Math.round(card.overrun * 100)}% of estimate`;
  }
  if (card.running && card.elapsedMinutes !== null && card.elapsedMinutes > STALE_MINUTES) {
    return "not stopped? running over 16h";
  }
  if (card.running && card.elapsedMinutes !== null && card.elapsedMinutes > 12 * 60) {
    return "running over 12h";
  }
  if (card.assignment.startedAt && !card.assignment.acceptedAt) {
    return "started without accepting";
  }
  return null;
}

export default async function LiveBoardPage() {
  const { cards } = await loadTaskBoard();

  const running = cards.filter((card) => card.running);
  const completed = cards.filter((card) => card.assignment.completedAt);
  const waiting = cards.filter(
    (card) => !card.assignment.startedAt && !card.assignment.completedAt
  );
  const flagged = cards.filter((card) => concern(card) !== null);

  const minutesToday = completed.reduce((total, card) => total + (card.elapsedMinutes ?? 0), 0);

  return (
    <>
      <PageHeader
        title="Live work"
        subtitle="Every task in progress, with the time the platform recorded when it started."
      />

      <section className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <Stat label="In progress" value={String(running.length)} />
        <Stat label="Not started" value={String(waiting.length)} />
        <Stat label="Finished" value={String(completed.length)} hint={formatDuration(minutesToday)} />
        <Stat
          label="Needs a look"
          value={String(flagged.length)}
          tone={flagged.length > 0 ? "warn" : "good"}
          hint="Over estimate or out of sequence"
        />
      </section>

      {cards.length === 0 ? (
        <div className="mt-4">
          <EmptyState
            message="No task assignments yet."
            detail="Assign a work order to a worker and it will appear here the moment they start."
          />
        </div>
      ) : (
        <div className="mt-4 overflow-hidden rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] shadow-[var(--shadow-card)]">
          <Table
            head={[
              { key: "worker", label: "Worker" },
              { key: "task", label: "Task" },
              { key: "started", label: "Started" },
              { key: "stopped", label: "Stopped" },
              { key: "worked", label: "Worked", numeric: true },
              { key: "estimate", label: "Estimate", numeric: true },
              { key: "state", label: "State" }
            ]}
          >
            {cards.map((card) => {
              const flag = concern(card);
              return (
                <tr key={card.assignment.id} className={rowClass}>
                  <td className="px-4 py-2.5">
                    <Link
                      href={`/work/${card.assignment.id}`}
                      className="font-medium underline-offset-2 hover:underline"
                    >
                      {card.worker?.fullName ?? "Unassigned"}
                    </Link>
                    {flag ? (
                      <span className="ml-2 text-xs text-[var(--color-warn)]">{flag}</span>
                    ) : null}
                  </td>
                  <td className="px-4 py-3 text-[var(--color-muted)]">
                    {card.order?.title ?? "—"}
                  </td>
                  <td className="px-4 py-3 tabular-nums">
                    {clockTime(card.assignment.startedAt)}
                  </td>
                  <td className="px-4 py-3 tabular-nums">
                    {clockTime(card.assignment.completedAt)}
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums">
                    {formatDuration(card.elapsedMinutes)}
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums text-[var(--color-muted)]">
                    {card.order?.estimatedHours ? `${card.order.estimatedHours}h` : "—"}
                  </td>
                  <td className="px-4 py-2.5">
                    <Badge value={card.assignment.status} />
                  </td>
                </tr>
              );
            })}
          </Table>
        </div>
      )}
    </>
  );
}

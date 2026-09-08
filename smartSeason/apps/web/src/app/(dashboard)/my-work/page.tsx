import Link from "next/link";
import { Badge, EmptyState, PageHeader } from "@/components/ui";
import { TaskTimer } from "@/components/task-timer";
import { loadMyWork, clockTime, formatDuration } from "@/lib/tasks";

export default async function MyWorkPage() {
  // task-service already scopes this to the caller, so nothing is filtered here.
  const mine = await loadMyWork();

  const open = mine.filter((card) => !card.assignment.completedAt);
  const done = mine.filter((card) => card.assignment.completedAt);

  return (
    <>
      <PageHeader
        title="My work"
        subtitle="Start a task when you begin and stop it when you finish. The time is recorded by the platform, not by your phone."
      />

      {open.length === 0 && done.length === 0 ? (
        <EmptyState
          message="Nothing assigned to you."
          detail="Assignments appear here as soon as a supervisor gives you a task."
        />
      ) : null}

      {open.length > 0 ? (
        <ul className="space-y-2">
          {open.map((card) => (
            <li
              key={card.assignment.id}
              className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3 transition-colors hover:border-[var(--color-faint)]"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <Link
                    href={`/work/${card.assignment.id}`}
                    className="text-xs font-semibold underline-offset-2 hover:underline"
                  >
                    {card.order?.title ?? "Task"}
                  </Link>
                  <p className="mt-0.5 text-2xs text-[var(--color-muted)]">
                    {card.order?.taskCode ?? "—"}
                    {card.order?.dueDate ? ` · due ${card.order.dueDate}` : ""}
                    {card.order?.estimatedHours
                      ? ` · estimated ${card.order.estimatedHours}h`
                      : ""}
                  </p>
                  {card.order?.description ? (
                    <p className="mt-1 max-w-2xl text-2xs leading-relaxed text-[var(--color-muted)]">
                      {card.order.description}
                    </p>
                  ) : null}
                  {card.assignment.startedAt ? (
                    <p className="mt-1 text-2xs text-[var(--color-muted)]">
                      Started {clockTime(card.assignment.startedAt)}
                    </p>
                  ) : null}
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {card.order ? <Badge value={card.order.priority} /> : null}
                  <TaskTimer
                    assignmentId={card.assignment.id}
                    startedAt={card.assignment.startedAt}
                    completedAt={card.assignment.completedAt}
                  />
                </div>
              </div>
            </li>
          ))}
        </ul>
      ) : null}

      {done.length > 0 ? (
        <>
          <h2 className="mt-6 mb-2 text-2xs font-medium uppercase tracking-[0.07em] text-[var(--color-faint)]">
            Finished
          </h2>
          <ul className="space-y-1.5">
            {done.map((card) => (
              <li
                key={card.assignment.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] px-3 py-2 transition-colors hover:border-[var(--color-faint)]"
              >
                <Link
                  href={`/work/${card.assignment.id}`}
                  className="text-xs underline-offset-2 hover:underline"
                >
                  {card.order?.title ?? "Task"}
                </Link>
                <span className="text-2xs tabular-nums text-[var(--color-muted)]">
                  {clockTime(card.assignment.startedAt)} – {clockTime(card.assignment.completedAt)}
                  {" · "}
                  {formatDuration(card.elapsedMinutes)}
                </span>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </>
  );
}

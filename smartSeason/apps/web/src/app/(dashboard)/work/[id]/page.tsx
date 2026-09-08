import Link from "next/link";
import { notFound } from "next/navigation";
import {
  Badge,
  EmptyState,
  PageHeader,
  Table,
  rowClass,
  secondaryButtonClass
} from "@/components/ui";
import { TaskTimer } from "@/components/task-timer";
import { auditTrailFor } from "@/lib/audit";
import { readRoles, readToken, readUserId } from "@/lib/session";
import { clockTime, formatDuration, loadTaskDetail } from "@/lib/tasks";

export const dynamic = "force-dynamic";

function stamp(value: string | null): string {
  if (!value) return "—";
  const moment = new Date(value);
  if (Number.isNaN(moment.getTime())) return "—";
  return `${moment.toISOString().slice(0, 10)} ${clockTime(value)}`;
}

export default async function TaskDetailPage({
  params
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const detail = await loadTaskDetail(id);
  if (!detail || !detail.card) {
    notFound();
  }

  const { card, checklist, evidence } = detail;
  const [token, userId, roles] = await Promise.all([readToken(), readUserId(), readRoles()]);
  const trail = await auditTrailFor(id, token);

  const isMine = card.worker?.id === userId;
  const canRun = isMine || roles.includes("ADMIN") || roles.includes("SUPERVISOR");
  const order = card.order;

  const timeline: [string, string | null][] = [
    ["Assigned", card.assignment.assignedAt],
    ["Accepted", card.assignment.acceptedAt],
    ["Started", card.assignment.startedAt],
    ["Stopped", card.assignment.completedAt]
  ];

  return (
    <>
      <PageHeader
        title={order?.title ?? "Task"}
        subtitle={`${order?.taskCode ?? "—"} · assigned to ${card.worker?.fullName ?? "nobody yet"}`}
        actions={
          <>
            {canRun ? (
              <TaskTimer
                assignmentId={card.assignment.id}
                startedAt={card.assignment.startedAt}
                completedAt={card.assignment.completedAt}
              />
            ) : null}
            <Link href="/live" className={secondaryButtonClass}>
              Live board
            </Link>
          </>
        }
      />

      <div className="grid gap-2.5 lg:grid-cols-3">
        <section className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3 lg:col-span-2">
          <h2 className="mb-2 text-2xs font-medium uppercase tracking-[0.07em] text-[var(--color-faint)]">
            The task
          </h2>
          <p className="text-xs leading-relaxed">
            {order?.description ?? "No description was recorded for this task."}
          </p>
          <dl className="mt-3 grid gap-x-8 gap-y-2 sm:grid-cols-3">
            {([
              ["Priority", order?.priority ?? "—"],
              ["Due", order?.dueDate ?? "—"],
              ["Estimated", order?.estimatedHours ? `${order.estimatedHours}h` : "—"],
              ["Work order", order?.status ?? "—"],
              ["Assignment", card.assignment.status],
              ["Worked", formatDuration(card.elapsedMinutes)]
            ] as [string, string][]).map(([label, value]) => (
              <div key={label}>
                <dt className="text-2xs uppercase tracking-[0.06em] text-[var(--color-faint)]">
                  {label}
                </dt>
                <dd className="mt-0.5 text-xs">{value}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3">
          <h2 className="mb-2 text-2xs font-medium uppercase tracking-[0.07em] text-[var(--color-faint)]">
            Timeline
          </h2>
          <ol className="space-y-1.5">
            {timeline.map(([label, value]) => (
              <li key={label} className="flex items-baseline justify-between gap-3 text-xs">
                <span className={value ? "" : "text-[var(--color-faint)]"}>{label}</span>
                <span className="tabular-nums text-[var(--color-muted)]">{stamp(value)}</span>
              </li>
            ))}
          </ol>
          {card.overrun !== null ? (
            <p
              className={`mt-2 text-2xs ${
                card.overrun > 1.5 ? "text-[var(--color-warn)]" : "text-[var(--color-muted)]"
              }`}
            >
              {Math.round(card.overrun * 100)}% of the estimated time
            </p>
          ) : null}
        </section>
      </div>

      <section className="mt-2.5 rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3">
        <h2 className="mb-2 text-2xs font-medium uppercase tracking-[0.07em] text-[var(--color-faint)]">
          Objectives
        </h2>
        {checklist.length === 0 ? (
          <p className="text-2xs text-[var(--color-muted)]">
            No checklist was attached to this work order.
          </p>
        ) : (
          <ul className="space-y-1">
            {checklist.map((item) => (
              <li key={item.id} className="flex items-baseline gap-2 text-xs">
                <span
                  className={
                    item.completed ? "text-[var(--color-good)]" : "text-[var(--color-faint)]"
                  }
                  aria-hidden="true"
                >
                  {item.completed ? "✓" : "○"}
                </span>
                <span className={item.completed ? "text-[var(--color-muted)] line-through" : ""}>
                  {item.label}
                </span>
                {item.required ? (
                  <span className="text-2xs text-[var(--color-faint)]">required</span>
                ) : null}
                {item.completedAt ? (
                  <span className="ml-auto text-2xs tabular-nums text-[var(--color-muted)]">
                    {stamp(item.completedAt)}
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="mt-2.5 rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3">
        <h2 className="mb-2 text-2xs font-medium uppercase tracking-[0.07em] text-[var(--color-faint)]">
          Evidence
        </h2>
        {evidence.length === 0 ? (
          <p className="text-2xs text-[var(--color-muted)]">Nothing attached.</p>
        ) : (
          <ul className="space-y-1">
            {evidence.map((item) => (
              <li key={item.id} className="flex items-baseline gap-2 text-xs">
                <Badge value={item.evidenceType} />
                <span className="text-[var(--color-muted)]">{item.notes ?? "—"}</span>
                {item.mockLocation ? (
                  <span className="text-2xs text-[var(--color-danger)]">mock location</span>
                ) : null}
                <span className="ml-auto text-2xs tabular-nums text-[var(--color-muted)]">
                  {stamp(item.capturedAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="mt-2.5 overflow-hidden rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)]">
        <div className="px-3 pt-3">
          <h2 className="text-2xs font-medium uppercase tracking-[0.07em] text-[var(--color-faint)]">
            Audit log
          </h2>
          <p className="mt-1 text-2xs text-[var(--color-muted)]">
            Each entry is hashed together with the one before it, so an altered record breaks
            every hash that follows.
          </p>
        </div>
        {trail.length === 0 ? (
          <EmptyState message="Nothing recorded for this task yet." />
        ) : (
          <div className="mt-2">
            <Table
              head={[
                { key: "seq", label: "#", numeric: true },
                { key: "action", label: "Action" },
                { key: "role", label: "Role" },
                { key: "when", label: "When" },
                { key: "outcome", label: "Outcome" },
                { key: "hash", label: "Record hash" }
              ]}
            >
              {trail.map((row) => (
                <tr key={row.id} className={rowClass}>
                  <td className="px-3 py-1.5 text-right tabular-nums">{row.sequence}</td>
                  <td className="px-3 py-1.5 font-medium">{row.action}</td>
                  <td className="px-3 py-1.5 text-[var(--color-muted)]">{row.actorRole ?? "—"}</td>
                  <td className="px-3 py-1.5 tabular-nums">{stamp(row.occurredAt)}</td>
                  <td className="px-3 py-1.5">
                    <Badge value={row.outcome} />
                  </td>
                  <td
                    className="px-3 py-1.5 font-mono text-2xs text-[var(--color-muted)]"
                    title={row.recordHash}
                  >
                    {row.recordHash.slice(0, 12)}…
                  </td>
                </tr>
              ))}
            </Table>
          </div>
        )}
      </section>
    </>
  );
}

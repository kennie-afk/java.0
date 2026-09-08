"use client";

import { useEffect, useState, useTransition } from "react";
import { startTask, stopTask } from "@/lib/task-actions";
import { buttonClass, dangerButtonClass } from "@/components/ui";

function elapsed(sinceIso: string): string {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(sinceIso).getTime()) / 1000));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${pad(hours)}:${pad(minutes)}:${pad(rest)}`;
}

/**
 * Start/stop control for one assignment.
 *
 * The ticking figure is display only — it is counted from the server's recorded
 * start time, and the stop time is stamped on the server too, so nothing the
 * browser does changes what gets recorded.
 */
export function TaskTimer({
  assignmentId,
  startedAt,
  completedAt,
  compact
}: {
  assignmentId: string;
  startedAt: string | null;
  completedAt: string | null;
  compact?: boolean;
}) {
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);
  const running = Boolean(startedAt) && !completedAt;
  const [tick, setTick] = useState(() => (startedAt ? elapsed(startedAt) : "00:00:00"));

  useEffect(() => {
    if (!running || !startedAt) return;
    setTick(elapsed(startedAt));
    const handle = setInterval(() => setTick(elapsed(startedAt)), 1000);
    return () => clearInterval(handle);
  }, [running, startedAt]);

  function run(action: () => Promise<{ ok: boolean; message: string | null }>) {
    setError(null);
    startTransition(async () => {
      const result = await action();
      if (!result.ok) setError(result.message ?? "That did not work");
    });
  }

  if (completedAt) {
    return <span className="text-2xs text-[var(--color-muted)]">Finished</span>;
  }

  return (
    <span className="inline-flex items-center gap-2">
      {running ? (
        <>
          <span
            className="tabular-nums font-medium text-[var(--color-good)]"
            aria-live="off"
          >
            {tick}
          </span>
          <button
            type="button"
            disabled={pending}
            onClick={() => run(() => stopTask(assignmentId))}
            className={dangerButtonClass}
          >
            {pending ? "Stopping…" : "Stop"}
          </button>
        </>
      ) : (
        <button
          type="button"
          disabled={pending}
          onClick={() => run(() => startTask(assignmentId))}
          className={buttonClass}
        >
          {pending ? "Starting…" : "Start"}
        </button>
      )}
      {error && !compact ? (
        <span className="text-2xs text-[var(--color-danger)]">{error}</span>
      ) : null}
    </span>
  );
}

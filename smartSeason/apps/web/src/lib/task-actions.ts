"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { api, ApiError } from "@/lib/api";
import { readRoles, readToken, readUserId } from "@/lib/session";
import { recordAudit } from "@/lib/audit";
import { primaryRole } from "@/lib/roles";
import type { FormState } from "@/lib/form-state";

const MY_WORK = "/api/task/v1/my-work";

/**
 * Starts or stops a task.
 *
 * The web application does not decide the time and does not decide who may act.
 * It posts to task-service, which stamps its own clock and refuses an
 * assignment that does not belong to the caller. Doing it here would be
 * theatre: anyone can call the API directly.
 */
async function mark(
  id: string,
  verb: "start" | "stop",
  action: string,
  revalidate: string[]
): Promise<FormState> {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }

  const [actorUserId, roles] = await Promise.all([readUserId(), readRoles()]);
  const actorRole = roles.length ? primaryRole(roles) : null;
  const occurredAt = new Date().toISOString();

  try {
    await api.post(`${MY_WORK}/${id}/${verb}`, {}, token);
  } catch (error) {
    // A refused action is logged too - a worker repeatedly failing to start a
    // task they were not assigned is exactly the kind of thing worth keeping.
    await recordAudit(
      {
        action, actorUserId, actorRole,
        resourceType: "TaskAssignment", resourceId: id,
        outcome: "FAILURE",
        occurredAt
      },
      token
    );
    if (error instanceof ApiError) {
      return {
        ok: false,
        message: error.problem?.detail ?? "The task could not be updated",
        fieldErrors: {}
      };
    }
    return { ok: false, message: "The platform is not reachable right now", fieldErrors: {} };
  }

  await recordAudit(
    {
      action, actorUserId, actorRole,
      resourceType: "TaskAssignment", resourceId: id,
      outcome: "SUCCESS",
      occurredAt,
      details: { verb }
    },
    token
  );

  for (const path of revalidate) {
    revalidatePath(path);
  }
  revalidatePath(`/work/${id}`);
  return { ok: true, message: null, fieldErrors: {} };
}

export async function startTask(id: string): Promise<FormState> {
  return mark(id, "start", "TASK_STARTED", ["/my-work", "/live"]);
}

export async function stopTask(id: string): Promise<FormState> {
  return mark(id, "stop", "TASK_STOPPED", ["/my-work", "/live"]);
}

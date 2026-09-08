import { api, type PageResponse } from "@/lib/api";
import { readToken } from "@/lib/session";

export interface WorkOrder {
  id: string;
  farmId: string;
  plotId: string | null;
  taskCode: string;
  title: string;
  description: string | null;
  dueDate: string | null;
  priority: string;
  estimatedHours: number | null;
  status: string;
}

export interface Assignment {
  id: string;
  workOrderId: string;
  workerId: string | null;
  workerUserId: string | null;
  gangId: string | null;
  assignedBy: string;
  assignedAt: string;
  acceptedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  status: string;
}

export interface Worker {
  id: string;
  userId: string | null;
  fullName: string;
  phone: string | null;
  riskScore: number;
  status: string;
}

export interface ChecklistItem {
  id: string;
  workOrderId: string;
  label: string;
  sequence: number;
  required: boolean;
  completed: boolean;
  completedAt: string | null;
  completedBy: string | null;
}

export interface TaskEvidence {
  id: string;
  assignmentId: string;
  evidenceType: string;
  mediaUrl: string | null;
  latitude: number | null;
  longitude: number | null;
  capturedAt: string | null;
  mockLocation: boolean;
  notes: string | null;
  verdict: string;
}

/** An assignment joined to the work order and worker it refers to. */
export interface TaskCard {
  assignment: Assignment;
  order: WorkOrder | null;
  worker: Worker | null;
  /** Minutes between start and stop, or start and now while running. */
  elapsedMinutes: number | null;
  running: boolean;
  /** Worked time as a fraction of the estimate; null when there is no estimate. */
  overrun: number | null;
}

async function page<T>(path: string, token: string | null): Promise<T[]> {
  try {
    return (await api.get<PageResponse<T>>(`${path}?size=200`, token)).content;
  } catch {
    return [];
  }
}

export function minutesBetween(from: string, to: string | null): number {
  const start = new Date(from).getTime();
  const end = to ? new Date(to).getTime() : Date.now();
  return Math.max(0, Math.round((end - start) / 60000));
}

export function formatDuration(minutes: number | null): string {
  if (minutes === null) return "—";
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return hours > 0 ? `${hours}h ${rest}m` : `${rest}m`;
}

/** Wall-clock time of day, which is what an employer checks first. */
export function clockTime(value: string | null): string {
  if (!value) return "—";
  const moment = new Date(value);
  if (Number.isNaN(moment.getTime())) return "—";
  return moment.toLocaleTimeString("en-KE", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}

export function buildCards(
  assignments: Assignment[],
  orders: WorkOrder[],
  workers: Worker[]
): TaskCard[] {
  const orderById = new Map(orders.map((order) => [order.id, order]));
  const workerById = new Map(workers.map((worker) => [worker.id, worker]));
  // An assignment may name the worker record, the signed-in account, or both.
  // Ownership is decided by the account, so that is the surer key of the two.
  const workerByUserId = new Map(
    workers.filter((worker) => worker.userId).map((worker) => [worker.userId as string, worker])
  );

  return assignments.map((assignment) => {
    const order = orderById.get(assignment.workOrderId) ?? null;
    const running = Boolean(assignment.startedAt) && !assignment.completedAt;
    const elapsedMinutes = assignment.startedAt
      ? minutesBetween(assignment.startedAt, assignment.completedAt)
      : null;

    const estimate = order?.estimatedHours ?? null;
    const overrun =
      estimate && estimate > 0 && elapsedMinutes !== null
        ? elapsedMinutes / 60 / estimate
        : null;

    return {
      assignment,
      order,
      worker:
        (assignment.workerUserId ? workerByUserId.get(assignment.workerUserId) : undefined) ??
        (assignment.workerId ? workerById.get(assignment.workerId) : undefined) ??
        null,
      elapsedMinutes,
      running,
      overrun
    };
  });
}

/** Everything needed to render one task in full, for worker and employer alike. */
export async function loadTaskDetail(assignmentId: string): Promise<{
  card: TaskCard | null;
  checklist: ChecklistItem[];
  evidence: TaskEvidence[];
} | null> {
  const token = await readToken();
  let assignment: Assignment;
  try {
    assignment = await api.get<Assignment>(
      `/api/task/v1/task-assignments/${assignmentId}`,
      token
    );
  } catch {
    return null;
  }

  const [orders, workers, checklist, evidence] = await Promise.all([
    page<WorkOrder>("/api/task/v1/work-orders", token),
    page<Worker>("/api/workforce/v1/workers", token),
    page<ChecklistItem>("/api/task/v1/checklist-items", token),
    page<TaskEvidence>("/api/task/v1/task-evidence", token)
  ]);

  const [card] = buildCards([assignment], orders, workers);
  return {
    card,
    checklist: checklist
      .filter((item) => item.workOrderId === assignment.workOrderId)
      .sort((a, b) => a.sequence - b.sequence),
    evidence: evidence.filter((item) => item.assignmentId === assignmentId)
  };
}

/**
 * The signed-in worker's own assignments.
 *
 * Scoped by task-service from the token, not filtered here — the API returns
 * only what belongs to the caller, so there is nothing to trim.
 */
export async function loadMyWork(): Promise<TaskCard[]> {
  const token = await readToken();
  let assignments: Assignment[] = [];
  try {
    assignments = await api.get<Assignment[]>("/api/task/v1/my-work", token);
  } catch {
    return [];
  }

  const [orders, workers] = await Promise.all([
    page<WorkOrder>("/api/task/v1/work-orders", token),
    page<Worker>("/api/workforce/v1/workers", token)
  ]);
  return buildCards(assignments, orders, workers);
}

export async function loadTaskBoard(): Promise<{
  cards: TaskCard[];
  reachable: boolean;
}> {
  const token = await readToken();
  const [assignments, orders, workers] = await Promise.all([
    page<Assignment>("/api/task/v1/task-assignments", token),
    page<WorkOrder>("/api/task/v1/work-orders", token),
    page<Worker>("/api/workforce/v1/workers", token)
  ]);

  return {
    cards: buildCards(assignments, orders, workers),
    // work-orders being empty is normal; a failed fetch of both is not.
    reachable: assignments.length > 0 || orders.length > 0
  };
}

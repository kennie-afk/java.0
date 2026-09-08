import { api } from "@/lib/api";

const TRAIL = "/api/audit/v1/audit-trail";

export interface AuditEntry {
  action: string;
  actorUserId: string | null;
  actorRole: string | null;
  resourceType: string;
  resourceId: string;
  outcome: "SUCCESS" | "FAILURE" | "DENIED";
  occurredAt: string;
  details?: Record<string, unknown>;
}

export interface AuditRow {
  id: string;
  sequence: number;
  action: string;
  outcome: string;
  occurredAt: string;
  actorRole: string | null;
  recordHash: string;
  previousHash: string | null;
}

/**
 * Appends one entry to the audit chain.
 *
 * The sequence number and both hashes are assigned by audit-service, not here:
 * a log whose writer picks its own position and hash proves nothing. This is
 * best-effort — a failure must never undo the action it describes — so it
 * returns false rather than throwing.
 */
export async function recordAudit(entry: AuditEntry, token: string | null): Promise<boolean> {
  try {
    await api.post(
      TRAIL,
      {
        serviceName: "web",
        actorUserId: entry.actorUserId,
        actorRole: entry.actorRole,
        action: entry.action,
        resourceType: entry.resourceType,
        resourceId: entry.resourceId,
        outcome: entry.outcome,
        occurredAt: entry.occurredAt,
        details: entry.details ? JSON.stringify(entry.details) : null
      },
      token
    );
    return true;
  } catch {
    return false;
  }
}

/** Entries recorded against one resource, oldest first. */
export async function auditTrailFor(
  resourceId: string,
  token: string | null
): Promise<AuditRow[]> {
  try {
    return await api.get<AuditRow[]>(`${TRAIL}/resource/${encodeURIComponent(resourceId)}`, token);
  } catch {
    return [];
  }
}

export interface ChainVerification {
  intact: boolean;
  brokenAtSequence: number;
  reason: string | null;
}

/** Recomputes the whole chain server-side and reports the first break. */
export async function verifyChain(token: string | null): Promise<ChainVerification | null> {
  try {
    return await api.get<ChainVerification>(`${TRAIL}/verify`, token);
  } catch {
    return null;
  }
}

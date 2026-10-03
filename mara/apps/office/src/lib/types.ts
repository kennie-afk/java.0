export interface Branch { id: string; name: string; timezone: string; status: string }
export interface Terminal { id: string; label: string; branch_id: string; status: string; enrolled_at: string | null; last_seen_at: string | null }
export interface Staff { id: string; staff_number: string; display_name: string; role: string; branch_id: string | null; status: string; failed_attempts: number; locked_until: string | null }
export interface SalesRow {
  day: string; terminalId?: string; cashierStaffId?: string; sales: number; totalMinor: number; taxMinor: number;
  cashMinor: number; mobileMinor: number; fiscalPending: number; inconsistent: number;
}
export interface CoreException { id: number; terminalId: string; sequence: number; kind: string; detail: string; raisedAt: string }
export interface SyncException { id: number; terminalId: string; sequence: number; kind: string; detail: string; raisedAt: string }
export interface AuditRow { id: number; occurred_at: string; actor: string; terminal_id: string | null; action: string; outcome: string; detail: string | null }

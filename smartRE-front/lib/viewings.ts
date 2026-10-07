import type { ViewingResponse } from '@/types'

/**
 * A no-show can be reported only for a confirmed viewing whose scheduled time has passed. The
 * server decides (and says so if this is wrong), but a button that is only offered when it can
 * work is better than one that is offered and then refused.
 *
 * `scheduledAt` arrives as a local date-time with no zone, which `Date` reads as the browser's
 * own zone: the same reading the viewings list uses to show it.
 */
export function canReportNoShow(v: Pick<ViewingResponse, 'status' | 'scheduledAt'>, now: Date = new Date()): boolean {
  if (v.status !== 'CONFIRMED') return false
  const at = new Date(v.scheduledAt)
  return !Number.isNaN(at.getTime()) && at.getTime() <= now.getTime()
}

import { describe, it, expect } from 'vitest'
import { canReportNoShow } from './viewings'

const now = new Date('2026-10-06T12:00:00')

describe('canReportNoShow', () => {
  it('is offered for a confirmed viewing whose time has passed', () => {
    expect(canReportNoShow({ status: 'CONFIRMED', scheduledAt: '2026-10-06T09:00:00' }, now)).toBe(true)
  })

  it('is not offered before the viewing happens', () => {
    expect(canReportNoShow({ status: 'CONFIRMED', scheduledAt: '2026-10-06T15:00:00' }, now)).toBe(false)
  })

  it('is not offered for any viewing that is not confirmed', () => {
    for (const status of ['PENDING_FEE', 'REQUESTED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'] as const) {
      expect(canReportNoShow({ status, scheduledAt: '2026-10-01T09:00:00' }, now)).toBe(false)
    }
  })

  it('is not offered when the time cannot be read', () => {
    expect(canReportNoShow({ status: 'CONFIRMED', scheduledAt: 'soon' }, now)).toBe(false)
  })
})

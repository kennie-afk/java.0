import { describe, it, expect, vi, beforeEach } from 'vitest'

// The api module builds its axios instance on import, so the mock has to exist before it loads.
const http = vi.hoisted(() => ({
  get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(),
  interceptors: { response: { use: vi.fn() } },
}))

vi.mock('axios', () => ({ default: { create: () => http } }))
vi.mock('react-hot-toast', () => ({ default: { error: vi.fn(), success: vi.fn() } }))

import { propertyApi, viewingApi, verifApi, pmsApi, revenueApi } from './api'

const reply = (data: unknown, headers: Record<string, string> = {}) => Promise.resolve({ data, headers })

beforeEach(() => {
  Object.values(http).forEach(fn => typeof fn === 'function' && (fn as ReturnType<typeof vi.fn>).mockReset?.())
})

describe('publish', () => {
  it('moves a managed property onto the marketplace with a PUT and no body', async () => {
    http.put.mockReturnValue(reply({ id: 'p1', status: 'PENDING_VERIFICATION' }))

    const result = await propertyApi.publish('p1')

    expect(http.put).toHaveBeenCalledWith('/api/properties/p1/publish', undefined)
    expect(result.status).toBe('PENDING_VERIFICATION')
  })
})

describe('no-show', () => {
  it('reports through PUT /no-show with the reason as a query parameter, as cancel does', async () => {
    http.put.mockReturnValue(reply({ id: 'v1', status: 'NO_SHOW' }))

    await viewingApi.noShow('v1', 'waited 30 minutes')

    expect(http.put).toHaveBeenCalledWith('/api/viewings/v1/no-show', undefined, { params: { reason: 'waited 30 minutes' } })
  })

  it('sends no reason when none was given', async () => {
    http.put.mockReturnValue(reply({}))

    await viewingApi.noShow('v2')

    expect(http.put).toHaveBeenCalledWith('/api/viewings/v2/no-show', undefined, { params: { reason: undefined } })
  })
})

describe('bulk ownership documents', () => {
  it('posts the whole folder, with no category, to the intake endpoint', async () => {
    const intake = { filed: { TITLE_DEED: ['deed.pdf'] }, needsReview: [], stillMissing: [], filedCount: 1, fullyAutomatic: true }
    http.post.mockReturnValue(reply(intake))
    const docs = [{ documentUrl: 'http://x/a.pdf', originalFilename: 'deed.pdf', mimeType: 'application/pdf', fileSizeBytes: 10 }]

    const result = await verifApi.bulkOwnerDocs('ov1', docs)

    expect(http.post).toHaveBeenCalledWith('/api/verification/ownership/ov1/documents/bulk', { documents: docs })
    expect(result.filedCount).toBe(1)
    expect(JSON.stringify(http.post.mock.calls[0][1])).not.toContain('documentCategory')
  })
})

describe('bounded lists', () => {
  it('keeps the array and reads the total and has-more from the headers', async () => {
    http.get.mockReturnValue(reply([{ id: 'u1' }], { 'x-total-count': '450', 'x-has-more': 'true' }))

    const page = await pmsApi.units.byPropertyPage('prop1', { page: 1, size: 200 })

    expect(http.get).toHaveBeenCalledWith('/api/units/property/prop1', { params: { page: 1, size: 200 } })
    expect(page).toEqual({ items: [{ id: 'u1' }], total: 450, hasMore: true })
  })

  it('falls back to the array length when the headers are absent, and says nothing more follows', async () => {
    http.get.mockReturnValue(reply([{ id: 'a' }, { id: 'b' }], {}))

    const page = await pmsApi.invoices.paymentsPage('inv1')

    expect(page).toEqual({ items: [{ id: 'a' }, { id: 'b' }], total: 2, hasMore: false })
  })

  it('the plain list entries still return the bare array existing callers parse', async () => {
    http.get.mockReturnValue(reply([{ id: 'u1' }], { 'x-total-count': '9' }))

    expect(await pmsApi.units.byProperty('prop1')).toEqual([{ id: 'u1' }])
    expect(await pmsApi.units.leases('u1')).toEqual([{ id: 'u1' }])
    expect(await pmsApi.invoices.payments('i1')).toEqual([{ id: 'u1' }])
  })

  it('pages the lease history and the raw callbacks through their own routes', async () => {
    http.get.mockReturnValue(reply([], { 'x-total-count': '0', 'x-has-more': 'false' }))

    await pmsApi.units.leasesPage('u9', { page: 2 })
    await revenueApi.rawCallbacks({ paymentId: 'pay1', limit: 50 })

    expect(http.get).toHaveBeenCalledWith('/api/units/u9/leases', { params: { page: 2 } })
    expect(http.get).toHaveBeenCalledWith('/api/revenue/callbacks/raw', { params: { paymentId: 'pay1', limit: 50 } })
  })
})

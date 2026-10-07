import { describe, it, expect } from 'vitest'
import { MAX_FILES, screenFiles } from './SmartIntakeUpload'

const file = (name: string, mb = 1) => ({ name, size: mb * 1024 * 1024 })

describe('screenFiles', () => {
  it('lets ordinary files through in order', () => {
    expect(screenFiles([file('a.pdf'), file('b.jpg')])).toEqual({ ok: [0, 1], refused: [] })
  })

  it('refuses a file over 10 MB with its name in the reason', () => {
    const { ok, refused } = screenFiles([file('big.pdf', 11), file('fine.pdf', 10)])
    expect(ok).toEqual([1])
    expect(refused).toEqual(['big.pdf is over 10 MB'])
  })

  it('stops at the 30 documents the server accepts in one call and says which were left out', () => {
    const many = Array.from({ length: MAX_FILES + 2 }, (_, i) => file(`d${i}.pdf`))
    const { ok, refused } = screenFiles(many)
    expect(ok).toHaveLength(MAX_FILES)
    expect(refused).toEqual([`d${MAX_FILES}.pdf: at most ${MAX_FILES} documents at a time`, `d${MAX_FILES + 1}.pdf: at most ${MAX_FILES} documents at a time`])
  })
})

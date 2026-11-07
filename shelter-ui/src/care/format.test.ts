import { describe, expect, it } from 'vitest'
import { care, noCare } from '../test/care'
import { formatDay, isCareEmpty, sterilizedLabel } from './format'

describe('care format', () => {
  it('shows a calendar date as it is, without a time zone shift', () => {
    expect(formatDay('2026-01-05')).toBe('5 Jan 2026')
    expect(formatDay('2026-12-31')).toBe('31 Dec 2026')
  })

  it('returns nothing for text that is not a date', () => {
    expect(formatDay('yesterday')).toBe('')
  })

  it('labels the three answers about sterilization', () => {
    expect(sterilizedLabel(true)).toBe('Yes')
    expect(sterilizedLabel(false)).toBe('No')
    expect(sterilizedLabel(null)).toBe('Not known')
  })

  it('knows when nothing has been entered', () => {
    expect(isCareEmpty(noCare)).toBe(true)
    expect(isCareEmpty(care())).toBe(false)
  })
})

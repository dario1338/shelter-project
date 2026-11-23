import { describe, expect, it } from 'vitest'
import { applicationDates, applicationStatusLabel, formatDate, NOTE_MAX, validateNote } from './format'

describe('sponsorship format', () => {
  it('accepts a note up to the limit and rejects a longer one', () => {
    expect(validateNote('a'.repeat(NOTE_MAX))).toBeUndefined()
    expect(validateNote('a'.repeat(NOTE_MAX + 1))).toMatch(/at most 1000/)
  })

  it('does not count the spaces around the note', () => {
    expect(validateNote(` ${'a'.repeat(NOTE_MAX)} `)).toBeUndefined()
  })

  it('names the statuses', () => {
    expect(applicationStatusLabel('CANCELLED')).toBe('Cancelled')
  })

  it('formats a date and tolerates a broken one', () => {
    expect(formatDate('2026-11-01T10:00:00Z')).toBe('1 Nov 2026')
    expect(formatDate('nonsense')).toBe('')
  })

  it('adds the decision date only when there is one', () => {
    expect(applicationDates({ createdAt: '2026-11-01T10:00:00Z', decidedAt: '2026-11-03T09:00:00Z' })).toBe(
      'Sent 1 Nov 2026 · Decided 3 Nov 2026',
    )
    expect(applicationDates({ createdAt: '2026-11-01T10:00:00Z', decidedAt: null })).toBe('Sent 1 Nov 2026')
    // An older API version does not send the field at all.
    expect(applicationDates({ createdAt: '2026-11-01T10:00:00Z' })).toBe('Sent 1 Nov 2026')
  })
})

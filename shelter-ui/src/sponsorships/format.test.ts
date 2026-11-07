import { describe, expect, it } from 'vitest'
import { applicationStatusLabel, formatDate, NOTE_MAX, validateNote } from './format'

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
})

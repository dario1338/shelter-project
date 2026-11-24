import { describe, expect, it } from 'vitest'
import { formatVisitTime, hourLabel, validateVisitDate, visitStatusLabel } from './format'

describe('visit format', () => {
  it('shows the time of a visit', () => {
    // A visit at 10:00 in the shelter, which the API sends as 09:00 UTC.
    const shown = formatVisitTime('2026-12-01T09:00:00Z')
    expect(shown).toContain('1 Dec 2026')
    expect(shown).toContain('10:00')
  })

  it('tolerates a broken time', () => {
    expect(formatVisitTime('nonsense')).toBe('')
  })

  it('names the hours and the statuses', () => {
    expect(hourLabel(9)).toBe('09:00')
    expect(hourLabel(15)).toBe('15:00')
    expect(visitStatusLabel('BOOKED')).toBe('Booked')
    expect(visitStatusLabel('CANCELLED')).toBe('Cancelled')
  })

  it('accepts only a day after today', () => {
    expect(validateVisitDate('', '2026-11-24')).toBe('Choose a day')
    expect(validateVisitDate('2026-11-24', '2026-11-24')).toBe('Choose a day after today')
    expect(validateVisitDate('2026-11-25', '2026-11-24')).toBeUndefined()
  })
})

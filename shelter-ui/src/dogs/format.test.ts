import { describe, expect, it } from 'vitest'
import { dogAge, emptyListMessage, parseDogId, statusLabel } from './format'

describe('statusLabel', () => {
  it('writes the status in words', () => {
    expect(statusLabel('AVAILABLE')).toBe('Available')
    expect(statusLabel('SPONSORED')).toBe('Sponsored')
    expect(statusLabel('ADOPTED')).toBe('Adopted')
  })
})

describe('dogAge', () => {
  it('counts the years from the year of birth', () => {
    expect(dogAge(2020, 2026)).toBe('6 years')
  })

  it('uses the singular for one year', () => {
    expect(dogAge(2025, 2026)).toBe('1 year')
  })

  it('says under one year for a dog born this year', () => {
    expect(dogAge(2026, 2026)).toBe('Under 1 year')
  })

  it('does not go negative for a year in the future', () => {
    expect(dogAge(2030, 2026)).toBe('Under 1 year')
  })

  it('returns nothing when the year is unknown', () => {
    expect(dogAge(null, 2026)).toBeNull()
  })

  it('uses the current year when none is given', () => {
    const lastYear = new Date().getFullYear() - 1
    expect(dogAge(lastYear)).toBe('1 year')
  })
})

describe('parseDogId', () => {
  it('accepts a positive whole number', () => {
    expect(parseDogId('7')).toBe(7)
    expect(parseDogId('1234')).toBe(1234)
  })

  it.each([undefined, '', '0', '-1', '1.5', 'abc', '12a', '007', ' 7', '99999999999999999999'])(
    'rejects %j',
    (text) => {
      expect(parseDogId(text)).toBeNull()
    },
  )
})

describe('emptyListMessage', () => {
  it('says there are no dogs when nothing is filtered', () => {
    expect(emptyListMessage(1, undefined)).toBe('There are no dogs yet.')
  })

  it('names the status that was chosen', () => {
    expect(emptyListMessage(1, 'ADOPTED')).toBe('There are no adopted dogs right now.')
  })

  it('says the page does not exist after the first page', () => {
    expect(emptyListMessage(3, 'ADOPTED')).toBe('This page does not exist.')
  })
})

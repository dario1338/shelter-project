import type { Care } from '../api/care'

// A date without a time, as 2026-01-31. It is read as a date of the calendar, not as a moment, so
// that no time zone can move it to the day before.
export function formatDay(day: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day)
  if (!match) {
    return ''
  }
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function sterilizedLabel(value: boolean | null): string {
  if (value === null) {
    return 'Not known'
  }
  return value ? 'Yes' : 'No'
}

// The API answers with an empty set for a dog nobody has entered anything for.
export function isCareEmpty(care: Care): boolean {
  return care.updatedAt === null
}

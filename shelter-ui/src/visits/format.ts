import type { VisitStatus } from '../api/visits'

// Visits start on the full hour, from 10:00 to 15:00, and take one hour.
export const VISIT_HOURS: readonly number[] = [10, 11, 12, 13, 14, 15]

const LABELS: Record<VisitStatus, string> = {
  BOOKED: 'Booked',
  CANCELLED: 'Cancelled',
}

export function visitStatusLabel(status: VisitStatus): string {
  return LABELS[status]
}

export function hourLabel(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`
}

// "Tue, 1 Dec 2026, 10:00"
export function formatVisitTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// The date field gives yyyy-mm-dd, or an empty string when nothing is chosen.
export function validateVisitDate(value: string, today: string): string | undefined {
  if (!value) {
    return 'Choose a day'
  }
  if (value <= today) {
    return 'Choose a day after today'
  }
  return undefined
}

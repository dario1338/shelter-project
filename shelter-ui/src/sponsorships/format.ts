import type { Application, ApplicationStatus } from '../api/sponsorships'

export const NOTE_MAX = 1000

const LABELS: Record<ApplicationStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
}

export const APPLICATION_STATUSES: readonly ApplicationStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']

export function isApplicationStatus(value: string): value is ApplicationStatus {
  return (APPLICATION_STATUSES as readonly string[]).includes(value)
}

export function applicationStatusLabel(status: ApplicationStatus): string {
  return LABELS[status]
}

// The API checks the length too. This only says it before the request is sent.
export function validateNote(value: string): string | undefined {
  if (value.trim().length > NOTE_MAX) {
    return `The note must be at most ${NOTE_MAX} characters`
  }
  return undefined
}

export function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

// "Sent 1 Nov 2026", and once the shelter has decided "Sent 1 Nov 2026 · Decided 3 Nov 2026".
// Without a decision date (pending, cancelled, or an older API that does not send it) only the first part is shown.
export function applicationDates(application: Pick<Application, 'createdAt' | 'decidedAt'>): string {
  const sent = `Sent ${formatDate(application.createdAt)}`
  const decided = application.decidedAt ? formatDate(application.decidedAt) : ''
  return decided ? `${sent} · Decided ${decided}` : sent
}

import type { ApplicationStatus } from '../api/sponsorships'
import { APPLICATION_STATUSES } from './format'

export interface ApplicationsView {
  // One-based, as shown to the user.
  page: number
  status: ApplicationStatus | undefined
}

// The administrator's list is described by the address (?status=PENDING&page=2), as the dog list is.
export function readApplicationsView(search: URLSearchParams): ApplicationsView {
  const status = APPLICATION_STATUSES.find((candidate) => candidate === search.get('status'))
  const pageText = search.get('page') ?? ''
  const page = /^[1-9]\d{0,5}$/.test(pageText) ? Number(pageText) : 1
  return { page, status }
}

export function writeApplicationsView({ page, status }: ApplicationsView): URLSearchParams {
  const search = new URLSearchParams()
  if (status) {
    search.set('status', status)
  }
  if (page > 1) {
    search.set('page', String(page))
  }
  return search
}

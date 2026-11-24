import { apiFetch } from './apiFetch'
import { toApiError } from './ApiError'

export type VisitStatus = 'BOOKED' | 'CANCELLED'

export interface Visit {
  id: number
  dogId: number
  dogName: string
  // The moment the visit starts, as the API sends it (ISO 8601, UTC).
  visitAt: string
  status: VisitStatus
  createdAt: string
}

export interface VisitPage {
  content: Visit[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export const VISIT_PAGE_SIZE = 10

async function send(path: string, init: RequestInit): Promise<Response> {
  const response = await apiFetch(path, init)
  if (!response.ok) {
    throw await toApiError(response)
  }
  return response
}

// The own visits, the earliest first (the API default).
export async function fetchMyVisits(page: number, signal?: AbortSignal): Promise<VisitPage> {
  const query = new URLSearchParams({ page: String(page), size: String(VISIT_PAGE_SIZE) })
  const response = await send(`/api/visits/me?${query.toString()}`, { signal })
  return (await response.json()) as VisitPage
}

// The day (yyyy-mm-dd) and the full hour of the visit in the shelter's time; the API works out the moment.
export async function bookVisit(dogId: number, date: string, hour: number): Promise<Visit> {
  const response = await send('/api/visits', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ dogId, date, hour }),
  })
  return (await response.json()) as Visit
}

export async function cancelVisit(id: number): Promise<Visit> {
  const response = await send(`/api/visits/${id}/cancel`, { method: 'POST' })
  return (await response.json()) as Visit
}

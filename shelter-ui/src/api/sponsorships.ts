import { apiFetch } from './apiFetch'
import { toApiError } from './ApiError'

export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface Application {
  id: number
  dogId: number
  dogName: string
  applicantEmail: string
  applicantName: string
  status: ApplicationStatus
  userNote: string | null
  adminNote: string | null
  createdAt: string
  updatedAt: string
}

export interface ApplicationPage {
  content: Application[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export const APPLICATION_PAGE_SIZE = 10

async function send(path: string, init: RequestInit): Promise<Response> {
  const response = await apiFetch(path, init)
  if (!response.ok) {
    throw await toApiError(response)
  }
  return response
}

// Zero-based page, as in the API. The newest application comes first (the API default).
export async function fetchMyApplications(page: number, signal?: AbortSignal): Promise<ApplicationPage> {
  const query = new URLSearchParams({ page: String(page), size: String(APPLICATION_PAGE_SIZE) })
  const response = await send(`/api/sponsorships/me?${query.toString()}`, { signal })
  return (await response.json()) as ApplicationPage
}

export async function applyForSponsorship(dogId: number, note: string): Promise<Application> {
  const body = note.trim() ? { dogId, note: note.trim() } : { dogId }
  const response = await send('/api/sponsorships', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return (await response.json()) as Application
}

export async function cancelApplication(id: number): Promise<Application> {
  const response = await send(`/api/sponsorships/${id}/cancel`, { method: 'POST' })
  return (await response.json()) as Application
}

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

export interface MyApplicationsParams {
  // Zero-based, as in the API.
  page: number
  size?: number
  dogId?: number
  status?: ApplicationStatus
}

// The own applications, the newest first (the API default). The filters narrow the list to one dog
// and/or one status.
export async function fetchMyApplications(params: MyApplicationsParams, signal?: AbortSignal): Promise<ApplicationPage> {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size ?? APPLICATION_PAGE_SIZE) })
  if (params.dogId !== undefined) {
    query.set('dogId', String(params.dogId))
  }
  if (params.status) {
    query.set('status', params.status)
  }
  const response = await send(`/api/sponsorships/me?${query.toString()}`, { signal })
  return (await response.json()) as ApplicationPage
}

export interface ApplicationListParams {
  page: number
  status?: ApplicationStatus
}

// All applications, for administrators. The newest first (the API default).
export async function fetchApplications({ page, status }: ApplicationListParams, signal?: AbortSignal): Promise<ApplicationPage> {
  const query = new URLSearchParams({ page: String(page), size: String(APPLICATION_PAGE_SIZE) })
  if (status) {
    query.set('status', status)
  }
  const response = await send(`/api/sponsorships?${query.toString()}`, { signal })
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

export type Decision = 'approve' | 'reject'

// Approving also sets the dog to SPONSORED and rejects the other pending applications for it.
export async function decideApplication(id: number, decision: Decision, adminNote: string): Promise<Application> {
  const body = adminNote.trim() ? { adminNote: adminNote.trim() } : {}
  const response = await send(`/api/sponsorships/${id}/${decision}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return (await response.json()) as Application
}

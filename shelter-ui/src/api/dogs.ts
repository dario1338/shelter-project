import { apiFetch } from './apiFetch'
import { toApiError } from './ApiError'

export type DogStatus = 'AVAILABLE' | 'SPONSORED' | 'ADOPTED'

export interface Dog {
  id: number
  name: string
  // The API stores these as optional, so a dog may have none of them.
  breed: string | null
  birthYear: number | null
  description: string | null
  status: DogStatus
}

export interface DogPage {
  content: Dog[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export const DOG_PAGE_SIZE = 12

export interface DogListParams {
  // Zero-based, as in the API.
  page: number
  status?: DogStatus
}

// The API has no default order, and without one the database may return the same dog on two pages
// or none at all. The order is therefore always asked for: by name, with the id breaking ties.
function listUrl({ page, status }: DogListParams): string {
  const query = new URLSearchParams({ page: String(page), size: String(DOG_PAGE_SIZE) })
  query.append('sort', 'name,asc')
  query.append('sort', 'id,asc')
  if (status) {
    query.set('status', status)
  }
  return `/api/dogs?${query.toString()}`
}

// Reading dogs needs no sign-in. apiFetch is used all the same, because a request that carries an
// expired token is answered with 401 even on a public path, and apiFetch then renews the session.
export async function fetchDogs(params: DogListParams, signal?: AbortSignal): Promise<DogPage> {
  const response = await apiFetch(listUrl(params), { signal })
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as DogPage
}

export async function fetchDog(id: number, signal?: AbortSignal): Promise<Dog> {
  const response = await apiFetch(`/api/dogs/${id}`, { signal })
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as Dog
}

// What the API accepts for a dog. An update replaces all of it, so a missing value must be sent as null.
export interface DogInput {
  name: string
  breed: string | null
  birthYear: number | null
  description: string | null
  status: DogStatus
}

async function sendDog(path: string, method: 'POST' | 'PUT', input: DogInput): Promise<Dog> {
  const response = await apiFetch(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as Dog
}

export function createDog(input: DogInput): Promise<Dog> {
  return sendDog('/api/dogs', 'POST', input)
}

export function updateDog(id: number, input: DogInput): Promise<Dog> {
  return sendDog(`/api/dogs/${id}`, 'PUT', input)
}

// The API also removes the applications and the care details of the dog.
export async function deleteDog(id: number): Promise<void> {
  const response = await apiFetch(`/api/dogs/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await toApiError(response)
  }
}

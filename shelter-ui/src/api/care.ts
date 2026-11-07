import { apiFetch } from './apiFetch'
import { toApiError } from './ApiError'

export interface Vaccination {
  id: number
  name: string
  // Dates without a time, as 2026-01-31.
  vaccinatedOn: string
  nextDueOn: string | null
}

export interface Care {
  sterilized: boolean | null
  microchipNumber: string | null
  healthNotes: string | null
  // Filled for administrators only.
  internalNote: string | null
  vaccinations: Vaccination[]
  // Null while nobody has entered anything for the dog.
  updatedAt: string | null
}

export interface VaccinationInput {
  name: string
  vaccinatedOn: string
  nextDueOn: string | null
}

// An update replaces everything, the vaccinations too.
export interface CareInput {
  sterilized: boolean | null
  microchipNumber: string | null
  healthNotes: string | null
  internalNote: string | null
  vaccinations: VaccinationInput[]
}

// Allowed for administrators and for the approved sponsor of the dog. Others get 403.
export async function fetchCare(dogId: number, signal?: AbortSignal): Promise<Care> {
  const response = await apiFetch(`/api/dogs/${dogId}/care`, { signal })
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as Care
}

export async function saveCare(dogId: number, input: CareInput): Promise<Care> {
  const response = await apiFetch(`/api/dogs/${dogId}/care`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as Care
}

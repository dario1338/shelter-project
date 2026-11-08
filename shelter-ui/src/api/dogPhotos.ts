import { apiFetch } from './apiFetch'
import { ApiError, toApiError } from './ApiError'
import type { DogPhoto } from './dogs'

const TOO_LARGE = 'The photo is too large (at most 5 MB)'

// The file goes as a multipart form. The Content-Type header is left to the browser, because only
// the browser knows the boundary that separates the parts.
export async function uploadDogPhoto(dogId: number, file: File): Promise<DogPhoto> {
  const body = new FormData()
  body.append('file', file)
  const response = await apiFetch(`/api/dogs/${dogId}/images`, { method: 'POST', body })
  if (!response.ok) {
    // The proxy in front of the API answers a body that is too large with an HTML page.
    if (response.status === 413) {
      throw new ApiError(413, TOO_LARGE)
    }
    throw await toApiError(response)
  }
  return (await response.json()) as DogPhoto
}

export async function deleteDogPhoto(dogId: number, photoId: number): Promise<void> {
  const response = await apiFetch(`/api/dogs/${dogId}/images/${photoId}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await toApiError(response)
  }
}

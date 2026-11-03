import { apiFetch } from '../api/apiFetch'
import { toApiError } from '../api/ApiError'

export interface CurrentUser {
  email: string
  role: string
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await apiFetch('/api/auth/me')
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as CurrentUser
}
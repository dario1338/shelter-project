import { apiFetch } from '../api/apiFetch'
import { toApiError } from '../api/ApiError'

export type Role = 'USER' | 'ADMIN'

export interface CurrentUser {
  email: string
  firstName: string
  lastName: string
  role: Role
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await apiFetch('/api/auth/me')
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as CurrentUser
}

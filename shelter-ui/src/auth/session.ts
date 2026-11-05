import { toApiError } from '../api/ApiError'
import { clearAccessToken, setAccessToken } from './tokenStore'

const AUTH_BASE = '/api/auth'

interface TokenResponse {
  accessToken: string
}

export interface RegistrationData {
  email: string
  password: string
  firstName: string
  lastName: string
}

// Requests are same-origin (the browser reaches the API through shelter-proxy), so the default
// fetch credentials mode already sends the HttpOnly refresh cookie to /api/auth.

export async function login(email: string, password: string): Promise<void> {
  const response = await fetch(`${AUTH_BASE}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (!response.ok) {
    throw await toApiError(response)
  }
  const body = (await response.json()) as TokenResponse
  setAccessToken(body.accessToken)
}

// Creates the account only. The server does not sign the new user in, so the caller does that.
export async function register(data: RegistrationData): Promise<void> {
  const response = await fetch(`${AUTH_BASE}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  if (!response.ok) {
    throw await toApiError(response)
  }
}

let refreshInFlight: Promise<boolean> | null = null

async function requestRefresh(): Promise<boolean> {
  const response = await fetch(`${AUTH_BASE}/refresh`, { method: 'POST' })
  if (response.status === 401 || response.status === 403) {
    clearAccessToken()
    return false
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  const body = (await response.json()) as TokenResponse
  setAccessToken(body.accessToken)
  return true
}

// Single-flight: the server rotates the refresh token on every call and treats a second use of
// the same token as theft, so concurrent callers must share one request.
export function refreshSession(): Promise<boolean> {
  refreshInFlight ??= requestRefresh().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${AUTH_BASE}/logout`, { method: 'POST' })
  } finally {
    clearAccessToken()
  }
}
import { refreshSession } from '../auth/session'
import { getAccessToken } from '../auth/tokenStore'

function send(input: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return fetch(input, { ...init, headers })
}

// Calls the API with the current access token. On 401 it restores the session once and retries;
// if the session cannot be restored the original 401 response is returned to the caller.
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const response = await send(input, init)
  if (response.status !== 401) {
    return response
  }
  const restored = await refreshSession()
  return restored ? send(input, init) : response
}
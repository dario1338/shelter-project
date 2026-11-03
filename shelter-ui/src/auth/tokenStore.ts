// The access token lives only in memory. A page reload drops it and the session is restored
// through the HttpOnly refresh cookie, so scripts never hold a long-lived credential.
let accessToken: string | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string): void {
  accessToken = token
}

export function clearAccessToken(): void {
  accessToken = null
}
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAccessToken, setAccessToken } from '../auth/tokenStore'
import { jsonResponse } from '../test/http'
import { apiFetch } from './apiFetch'

const fetchMock = vi.fn<typeof fetch>()

function authorizationOf(callIndex: number): string | null {
  const init = fetchMock.mock.calls[callIndex][1]
  return new Headers(init?.headers).get('Authorization')
}

beforeEach(() => {
  clearAccessToken()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('apiFetch', () => {
  it('sends the bearer token when one is present', async () => {
    setAccessToken('t1')
    fetchMock.mockResolvedValueOnce(jsonResponse(200, {}))

    const response = await apiFetch('/api/dogs')

    expect(response.status).toBe(200)
    expect(authorizationOf(0)).toBe('Bearer t1')
  })

  it('sends no Authorization header without a token', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, {}))

    await apiFetch('/api/dogs')

    expect(authorizationOf(0)).toBeNull()
  })

  it('restores the session once and retries after a 401', async () => {
    setAccessToken('old')
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'new' }))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }))

    const response = await apiFetch('/api/dogs')

    expect(response.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(authorizationOf(0)).toBe('Bearer old')
    expect(authorizationOf(2)).toBe('Bearer new')
  })

  it('returns the 401 when the session cannot be restored', async () => {
    setAccessToken('old')
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))

    const response = await apiFetch('/api/dogs')

    expect(response.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse } from '../test/http'
import { login, logout, refreshSession } from './session'
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  clearAccessToken()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('login', () => {
  it('stores the access token returned by the API', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { accessToken: 'abc' }))

    await login('a@b.c', 'secret')

    expect(getAccessToken()).toBe('abc')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/login')
    expect(init?.body).toBe(JSON.stringify({ email: 'a@b.c', password: 'secret' }))
  })

  it('throws the problem detail and keeps no token when credentials are rejected', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { detail: 'Invalid credentials' }))

    await expect(login('a@b.c', 'bad')).rejects.toMatchObject({
      status: 401,
      message: 'Invalid credentials',
    })
    expect(getAccessToken()).toBeNull()
  })
})

describe('refreshSession', () => {
  it('shares one request between concurrent callers', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { accessToken: 'fresh' }))

    const results = await Promise.all([refreshSession(), refreshSession()])

    expect(results).toEqual([true, true])
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBe('fresh')
  })

  it('sends a new request once the previous one has finished', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'one' }))
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'two' }))

    await refreshSession()
    await refreshSession()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(getAccessToken()).toBe('two')
  })

  it('returns false and drops the token when the refresh cookie is rejected', async () => {
    setAccessToken('stale')
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))

    expect(await refreshSession()).toBe(false)
    expect(getAccessToken()).toBeNull()
  })

  it('throws on server errors', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }))

    await expect(refreshSession()).rejects.toMatchObject({ status: 500 })
  })
})

describe('logout', () => {
  it('drops the token even when the request fails', async () => {
    setAccessToken('t')
    fetchMock.mockRejectedValueOnce(new TypeError('network'))

    await expect(logout()).rejects.toThrow('network')
    expect(getAccessToken()).toBeNull()
  })
})
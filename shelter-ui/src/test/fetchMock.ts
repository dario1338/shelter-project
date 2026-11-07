import { afterEach, beforeEach, vi } from 'vitest'
import { clearAccessToken } from '../auth/tokenStore'

// A fresh fetch mock for every test of the file that calls this, and no session or fetch left behind.
export function setUpFetchMock() {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    clearAccessToken()
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  return fetchMock
}

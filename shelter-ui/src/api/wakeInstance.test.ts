import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { wakeInstance } from './wakeInstance'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('wakeInstance', () => {
  it('asks the proxy for the instance name and sends one no-cors request straight to it', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response('shelter-api-green.onrender.com\n', { status: 200 }))
      .mockResolvedValueOnce(new Response('', { status: 200 }))

    await wakeInstance(new AbortController().signal)

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0][0]).toBe('/api-host')
    expect(fetchMock.mock.calls[1][0]).toBe('https://shelter-api-green.onrender.com/api/health')
    expect(fetchMock.mock.calls[1][1]?.mode).toBe('no-cors')
  })

  it('does nothing when the proxy has no such path', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>not found</html>', { status: 404 }))

    await wakeInstance(new AbortController().signal)

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it.each(['evil.example.com', 'shelter-api-green.onrender.com/x', 'https://shelter-api-green.onrender.com', '', '<html>'])(
    'never sends a request to a name that is not a Render host: %j',
    async (answer) => {
      fetchMock.mockResolvedValueOnce(new Response(answer, { status: 200 }))

      await wakeInstance(new AbortController().signal)

      expect(fetchMock).toHaveBeenCalledTimes(1)
    },
  )

  it('does not throw when the proxy cannot be reached or the direct request fails', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await expect(wakeInstance(new AbortController().signal)).resolves.toBeUndefined()

    fetchMock
      .mockResolvedValueOnce(new Response('shelter-api-blue.onrender.com', { status: 200 }))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await expect(wakeInstance(new AbortController().signal)).resolves.toBeUndefined()
  })

  it('gives up on a direct request that never ends after three minutes', async () => {
    vi.useFakeTimers()
    fetchMock
      .mockResolvedValueOnce(new Response('shelter-api-green.onrender.com', { status: 200 }))
      .mockImplementationOnce(
        (_input, init) =>
          new Promise((_resolve, reject) => {
            init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
          }),
      )

    const result = wakeInstance(new AbortController().signal)
    await vi.advanceTimersByTimeAsync(3 * 60_000 + 1000)

    await expect(result).resolves.toBeUndefined()
  })

  it('stops the direct request when the caller aborts', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response('shelter-api-green.onrender.com', { status: 200 }))
      .mockImplementationOnce(
        (_input, init) =>
          new Promise((_resolve, reject) => {
            init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
          }),
      )
    const controller = new AbortController()

    const result = wakeInstance(controller.signal)
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    controller.abort()

    await expect(result).resolves.toBeUndefined()
  })
})

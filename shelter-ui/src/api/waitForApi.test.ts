import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { waitForApi } from './waitForApi'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('waitForApi', () => {
  it('returns true at once when the API is awake', async () => {
    fetchMock.mockResolvedValueOnce(new Response('{}', { status: 200 }))

    await expect(waitForApi(new AbortController().signal)).resolves.toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/health')
  })

  it('keeps asking through 429, 502 and network failures until the API answers', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response('Too Many Requests', { status: 429 }))
      .mockResolvedValueOnce(new Response('', { status: 502 }))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(new Response('{}', { status: 200 }))

    const result = waitForApi(new AbortController().signal)
    await vi.advanceTimersByTimeAsync(60_000)

    await expect(result).resolves.toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  it('gives up after four minutes', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 429 }))

    const result = waitForApi(new AbortController().signal)
    await vi.advanceTimersByTimeAsync(5 * 60_000)

    await expect(result).resolves.toBe(false)
  })

  it('stops when the caller aborts', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 429 }))
    const controller = new AbortController()

    const result = waitForApi(controller.signal)
    await vi.advanceTimersByTimeAsync(1000)
    controller.abort()
    await vi.advanceTimersByTimeAsync(1000)

    await expect(result).resolves.toBe(false)
  })

  it('treats an attempt that never answers as a failure and tries again', async () => {
    fetchMock
      .mockImplementationOnce(
        (_input, init) =>
          new Promise((_resolve, reject) => {
            init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
          }),
      )
      .mockResolvedValueOnce(new Response('{}', { status: 200 }))

    const result = waitForApi(new AbortController().signal)
    await vi.advanceTimersByTimeAsync(40_000)

    await expect(result).resolves.toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})

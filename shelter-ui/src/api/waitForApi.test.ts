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

const PROXY_HEALTH = '/api/health'
const NO_WAKE = new Response('', { status: 404 })

// Answers per address, the way the browser would: the health check goes through the proxy, and the
// wake-up looks up the instance name first.
function answers(health: Array<Response | Error>, apiHost: Response = NO_WAKE.clone()) {
  const queue = [...health]
  fetchMock.mockImplementation((input) => {
    if (input === '/api-host') {
      return Promise.resolve(apiHost.clone())
    }
    if (input === PROXY_HEALTH) {
      const next = queue.length > 1 ? queue.shift() : queue[0]
      return next instanceof Error ? Promise.reject(next) : Promise.resolve(next!.clone())
    }
    return Promise.resolve(new Response('', { status: 200 }))
  })
}

function healthCalls(): number {
  return fetchMock.mock.calls.filter(([input]) => input === PROXY_HEALTH).length
}

describe('waitForApi', () => {
  it('returns true at once, without waking anything, when the API is awake', async () => {
    answers([new Response('{}', { status: 200 })])

    await expect(waitForApi(new AbortController().signal)).resolves.toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe(PROXY_HEALTH)
  })

  it('wakes the instance directly when the first check fails, then keeps asking through the proxy', async () => {
    answers(
      [
        new Response('Too Many Requests', { status: 429 }),
        new Response('Too Many Requests', { status: 429 }),
        new Response('{}', { status: 200 }),
      ],
      new Response('shelter-api-green.onrender.com', { status: 200 }),
    )

    const result = waitForApi(new AbortController().signal)
    await vi.advanceTimersByTimeAsync(60_000)

    await expect(result).resolves.toBe(true)
    const addresses = fetchMock.mock.calls.map(([input]) => input)
    expect(addresses).toContain('https://shelter-api-green.onrender.com/api/health')
    expect(addresses.indexOf('/api-host')).toBeGreaterThan(addresses.indexOf(PROXY_HEALTH))
  })

  it('keeps asking through 429, 502 and network failures until the API answers', async () => {
    answers([
      new Response('Too Many Requests', { status: 429 }),
      new Response('', { status: 502 }),
      new TypeError('Failed to fetch'),
      new Response('{}', { status: 200 }),
    ])

    const result = waitForApi(new AbortController().signal)
    await vi.advanceTimersByTimeAsync(60_000)

    await expect(result).resolves.toBe(true)
    expect(healthCalls()).toBe(4)
  })

  it('gives up four minutes after the wake-up request', async () => {
    answers([new Response('', { status: 429 })])

    const result = waitForApi(new AbortController().signal)
    await vi.advanceTimersByTimeAsync(5 * 60_000)

    await expect(result).resolves.toBe(false)
  })

  it('stops when the caller aborts', async () => {
    answers([new Response('', { status: 429 })])
    const controller = new AbortController()

    const result = waitForApi(controller.signal)
    await vi.advanceTimersByTimeAsync(1000)
    controller.abort()
    await vi.advanceTimersByTimeAsync(1000)

    await expect(result).resolves.toBe(false)
  })

  it('treats a check that never answers as a failure and tries again', async () => {
    let calls = 0
    fetchMock.mockImplementation((input, init) => {
      if (input !== PROXY_HEALTH) {
        return Promise.resolve(new Response('', { status: 404 }))
      }
      calls++
      if (calls === 1) {
        return new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
        })
      }
      return Promise.resolve(new Response('{}', { status: 200 }))
    })

    const result = waitForApi(new AbortController().signal)
    await vi.advanceTimersByTimeAsync(40_000)

    await expect(result).resolves.toBe(true)
    expect(calls).toBe(2)
  })
})

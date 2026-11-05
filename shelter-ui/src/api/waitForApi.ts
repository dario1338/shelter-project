const HEALTH_PATH = '/api/health'

// Free instances sleep when idle and need about two minutes to wake up. Until then the hosting
// platform answers with 429 or 5xx, or the request fails, so every such answer means "not yet".
const RETRY_DELAYS_MS = [2000, 3000, 5000, 8000]
const ATTEMPT_TIMEOUT_MS = 30_000
const GIVE_UP_AFTER_MS = 4 * 60_000

function pause(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms)
    signal.addEventListener(
      'abort',
      () => {
        clearTimeout(timer)
        resolve()
      },
      { once: true },
    )
  })
}

async function isHealthy(signal: AbortSignal): Promise<boolean> {
  const attempt = new AbortController()
  const timer = setTimeout(() => attempt.abort(), ATTEMPT_TIMEOUT_MS)
  const stop = () => attempt.abort()
  signal.addEventListener('abort', stop, { once: true })
  try {
    const response = await fetch(HEALTH_PATH, { cache: 'no-store', signal: attempt.signal })
    return response.ok
  } catch {
    return false
  } finally {
    clearTimeout(timer)
    signal.removeEventListener('abort', stop)
  }
}

// Resolves true as soon as the API answers its health endpoint, or false when it did not within
// four minutes or the caller aborted. The first request is also what wakes a sleeping instance.
export async function waitForApi(signal: AbortSignal): Promise<boolean> {
  const deadline = Date.now() + GIVE_UP_AFTER_MS
  for (let attempt = 0; !signal.aborted && Date.now() < deadline; attempt++) {
    if (await isHealthy(signal)) {
      return true
    }
    await pause(RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)], signal)
  }
  return false
}

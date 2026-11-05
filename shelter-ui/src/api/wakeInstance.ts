const API_HOST_PATH = '/api-host'

// The proxy answers with the name of the API instance that receives the traffic. Only a Render host
// name is accepted, so a wrong answer can never send the browser to an arbitrary address.
const RENDER_HOST = /^[a-z0-9][a-z0-9-]*\.onrender\.com$/

const WAKE_TIMEOUT_MS = 3 * 60_000

async function readInstanceHost(signal: AbortSignal): Promise<string | null> {
  try {
    const response = await fetch(API_HOST_PATH, { cache: 'no-store', signal })
    if (!response.ok) {
      return null
    }
    const host = (await response.text()).trim()
    return RENDER_HOST.test(host) ? host : null
  } catch {
    return null
  }
}

// Wakes the API instance with a request sent from the browser straight to it. The platform does not
// wake a sleeping free instance for requests that come through the proxy (it answers 429), but it does
// for requests from outside and holds them until the instance is up. The answer is not readable from
// another origin (no-cors) and it is not needed: the caller checks the health through the proxy
// afterwards. Resolves when the request ends or fails, and never throws.
export async function wakeInstance(signal: AbortSignal): Promise<void> {
  const host = await readInstanceHost(signal)
  if (host === null) {
    return
  }
  const request = new AbortController()
  const timer = setTimeout(() => request.abort(), WAKE_TIMEOUT_MS)
  const stop = () => request.abort()
  signal.addEventListener('abort', stop, { once: true })
  try {
    await fetch(`https://${host}/api/health`, { mode: 'no-cors', cache: 'no-store', signal: request.signal })
  } catch {
    // The request failed or was aborted; the health check through the proxy decides what happens next.
  } finally {
    clearTimeout(timer)
    signal.removeEventListener('abort', stop)
  }
}

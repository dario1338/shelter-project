export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

type Handler = (url: string, init?: RequestInit) => Response | Promise<Response>

// Answers fetch by address, so a test does not depend on the order in which the page asks. The first
// route whose prefix matches wins. The session restore every page starts with is answered "no session"
// unless the test says otherwise.
export function routeFetch(fetchMock: { mockImplementation: (fn: typeof fetch) => unknown }, routes: Record<string, Handler>) {
  const all: Record<string, Handler> = { '/api/auth/refresh': () => new Response(null, { status: 401 }), ...routes }
  fetchMock.mockImplementation((input, init) => {
    const url = String(input)
    const match = Object.keys(all).find((prefix) => url.startsWith(prefix))
    if (match === undefined) {
      throw new Error(`No route for ${url}`)
    }
    return Promise.resolve(all[match](url, init))
  })
}

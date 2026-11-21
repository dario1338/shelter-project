// The API sets a CSRF token as a cookie that scripts may read. It is not a secret: a page of another
// site can neither read it nor set it for this origin. The requests that depend on the refresh cookie
// send the same value in a header, and the server compares the header with the cookie.
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

function readCookie(name: string): string | null {
  for (const part of document.cookie.split('; ')) {
    const separator = part.indexOf('=')
    if (separator > 0 && part.slice(0, separator) === name) {
      return part.slice(separator + 1)
    }
  }
  return null
}

// The header is left out while the cookie is missing (before the API has set it), so a server that
// does not check the token yet still accepts the request.
export function csrfHeaders(): Record<string, string> {
  const token = readCookie(CSRF_COOKIE)
  return token ? { [CSRF_HEADER]: token } : {}
}

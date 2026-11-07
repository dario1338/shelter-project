// A sign-in page opened from a protected page remembers where the user wanted to go.
export interface ReturnState {
  from: string
}

// Only an address inside this application is accepted. Anything else (another site, "//host") falls
// back to the home page, so the state of a link cannot send the user away after signing in.
export function returnPathFrom(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state) {
    const value = (state as { from: unknown }).from
    if (typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') && !value.includes('\\')) {
      return value
    }
  }
  return '/'
}

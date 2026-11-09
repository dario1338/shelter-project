import { useSyncExternalStore } from 'react'

// A phone, or a window as narrow as one: below the width where the whole navigation fits in the header.
export const COMPACT = '(max-width: 47.99em)'
// A very large monitor (from 2200 px), where the dogs are shown five in a row. The same width as the
// "3xl" breakpoint of the theme (ui/system.ts), so that the page size and the columns agree.
export const VERY_WIDE = '(min-width: 137.5em)'

function subscribeTo(query: string) {
  return (onChange: () => void): (() => void) => {
    const list = window.matchMedia(query)
    list.addEventListener('change', onChange)
    return () => list.removeEventListener('change', onChange)
  }
}

export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    subscribeTo(query),
    () => window.matchMedia(query).matches,
    () => false,
  )
}

import { useSyncExternalStore } from 'react'

// A phone, or a window as narrow as one: below the width where the whole navigation fits in the header.
export const COMPACT = '(max-width: 47.99em)'
// A wide screen (from 1536 px, a Full HD monitor or a large laptop), where the dogs are shown five in a row.
// The same width as the "2xl" breakpoint of the theme, so that the page size and the columns agree.
export const VERY_WIDE = '(min-width: 96em)'

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

import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { COMPACT, VERY_WIDE } from '../ui/screens'

afterEach(() => {
  cleanup()
})

// jsdom has no matchMedia. By default no media query matches, so the screen is an ordinary wide one (a
// laptop); a test that needs a phone or a wide screen says so with the helpers of test/screen.ts.
export interface Screen {
  narrow?: boolean
  veryWide?: boolean
}

export function setScreen({ narrow = false, veryWide = false }: Screen = {}) {
  window.matchMedia = vi.fn((query: string) => ({
    matches: (query === COMPACT && narrow) || (query === VERY_WIDE && veryWide),
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(() => false),
  })) as unknown as typeof window.matchMedia
}

setScreen()
afterEach(() => setScreen())

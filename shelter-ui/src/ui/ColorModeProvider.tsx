import { useCallback, useLayoutEffect, useMemo, useState, type ReactNode } from 'react'
import { ColorModeContext, type ColorMode, type ColorModeContextValue } from './ColorModeContext'

const STORAGE_KEY = 'shelter-color-mode'

// Dark is the default. Only an explicit choice of light is remembered.
function readStoredMode(): ColorMode {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'light' ? 'light' : 'dark'
  } catch {
    // Storage can be blocked (private windows, site settings); the choice then lasts for the visit.
    return 'dark'
  }
}

function storeMode(mode: ColorMode): void {
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    // See readStoredMode: not being able to remember the choice is not an error.
  }
}

// Chakra switches its semantic colors on the "dark" class of the root element.
function applyMode(mode: ColorMode): void {
  const root = document.documentElement
  root.classList.toggle('dark', mode === 'dark')
  root.classList.toggle('light', mode === 'light')
  root.style.colorScheme = mode
}

export function ColorModeProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [mode, setMode] = useState<ColorMode>(readStoredMode)

  // A layout effect runs before the browser paints, so a visitor who chose light does not see a flash of dark.
  useLayoutEffect(() => {
    applyMode(mode)
  }, [mode])

  const toggle = useCallback(() => {
    setMode((current) => {
      const next = current === 'dark' ? 'light' : 'dark'
      storeMode(next)
      return next
    })
  }, [])

  const value = useMemo<ColorModeContextValue>(() => ({ mode, toggle }), [mode, toggle])

  return <ColorModeContext.Provider value={value}>{children}</ColorModeContext.Provider>
}

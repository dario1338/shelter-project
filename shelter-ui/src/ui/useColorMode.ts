import { useContext } from 'react'
import { ColorModeContext, type ColorModeContextValue } from './ColorModeContext'

export function useColorMode(): ColorModeContextValue {
  const value = useContext(ColorModeContext)
  if (value === null) {
    throw new Error('useColorMode must be used inside ColorModeProvider')
  }
  return value
}

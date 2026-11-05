import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ColorModeProvider } from './ColorModeProvider'
import { useColorMode } from './useColorMode'

function Probe() {
  const { mode, toggle } = useColorMode()
  return <button onClick={toggle}>mode:{mode}</button>
}

function renderProbe() {
  return render(
    <ColorModeProvider>
      <Probe />
    </ColorModeProvider>,
  )
}

const root = document.documentElement

beforeEach(() => {
  localStorage.clear()
  root.className = ''
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ColorModeProvider', () => {
  it('starts dark and marks the page as dark', () => {
    renderProbe()

    expect(screen.getByText('mode:dark')).toBeInTheDocument()
    expect(root).toHaveClass('dark')
    expect(root).not.toHaveClass('light')
    expect(root.style.colorScheme).toBe('dark')
  })

  it('switches to light, back to dark, and remembers the choice', () => {
    renderProbe()

    fireEvent.click(screen.getByRole('button'))
    expect(screen.getByText('mode:light')).toBeInTheDocument()
    expect(root).toHaveClass('light')
    expect(root).not.toHaveClass('dark')
    expect(localStorage.getItem('shelter-color-mode')).toBe('light')

    fireEvent.click(screen.getByRole('button'))
    expect(screen.getByText('mode:dark')).toBeInTheDocument()
    expect(root).toHaveClass('dark')
    expect(localStorage.getItem('shelter-color-mode')).toBe('dark')
  })

  it('opens in light when light was chosen earlier', () => {
    localStorage.setItem('shelter-color-mode', 'light')

    renderProbe()

    expect(screen.getByText('mode:light')).toBeInTheDocument()
    expect(root).toHaveClass('light')
  })

  it('works when the browser blocks storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })

    renderProbe()
    expect(screen.getByText('mode:dark')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button'))
    expect(screen.getByText('mode:light')).toBeInTheDocument()
  })
})

describe('useColorMode', () => {
  it('fails outside of a ColorModeProvider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    expect(() => render(<Probe />)).toThrow('useColorMode must be used inside ColorModeProvider')
  })
})

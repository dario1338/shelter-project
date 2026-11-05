import { ChakraProvider, defaultSystem } from '@chakra-ui/react'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ServerGate } from './ServerGate'

const fetchMock = vi.fn<typeof fetch>()

function renderGate() {
  return render(
    <ChakraProvider value={defaultSystem}>
      <ServerGate>
        <p>Application</p>
      </ServerGate>
    </ChakraProvider>,
  )
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.useFakeTimers({ shouldAdvanceTime: true })
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('ServerGate', () => {
  it('shows the application without any waiting screen when the server is awake', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }))

    renderGate()

    expect(await screen.findByText('Application')).toBeInTheDocument()
    expect(screen.queryByText(/waking up/i)).not.toBeInTheDocument()
  })

  it('shows nothing during the first moments, then the waiting screen, then the application', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response('Too Many Requests', { status: 429 }))
      .mockResolvedValue(new Response('{}', { status: 200 }))

    renderGate()
    expect(screen.queryByText(/waking up/i)).not.toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(700))
    expect(screen.getByText(/waking up the server/i)).toBeInTheDocument()
    expect(screen.queryByText('Application')).not.toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(3000))
    expect(await screen.findByText('Application')).toBeInTheDocument()
    expect(screen.queryByText(/waking up/i)).not.toBeInTheDocument()
  })

  it('offers a retry when the server does not respond, and recovers on the second try', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 429 }))

    renderGate()
    await act(() => vi.advanceTimersByTimeAsync(5 * 60_000))

    expect(await screen.findByText(/did not respond/i)).toBeInTheDocument()

    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }))
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Application')).toBeInTheDocument()
  })
})

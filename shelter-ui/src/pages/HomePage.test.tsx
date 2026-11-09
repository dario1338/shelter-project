import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dog, dogPage } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'

const fetchMock = setUpFetchMock()

function serve(dogs: Response | (() => Response)) {
  routeFetch(fetchMock, { '/api/dogs': typeof dogs === 'function' ? dogs : () => dogs })
}

describe('home page', () => {
  it('welcomes the visitor and shows the newest dogs that look for a sponsor', async () => {
    serve(() => jsonResponse(200, dogPage([dog({ id: 5, name: 'Luna' }), dog({ id: 4, name: 'Rex' })])))

    renderApp()

    expect(await screen.findByRole('link', { name: 'Luna' })).toHaveAttribute('href', '/dogs/5')
    expect(screen.getByRole('link', { name: 'Rex' })).toHaveAttribute('href', '/dogs/4')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Welcome to Shelter')
    expect(screen.getByText(/Help dogs find their forever home/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Meet our dogs' })).toBeInTheDocument()
  })

  it('asks the API for the six newest dogs that are available', async () => {
    serve(() => jsonResponse(200, dogPage([])))

    renderApp()
    await screen.findByText(/No dog is looking for a sponsor/)

    const url = new URL(String(fetchMock.mock.calls.find(([address]) => String(address).startsWith('/api/dogs'))![0]), 'http://x')
    expect(url.searchParams.get('size')).toBe('6')
    expect(url.searchParams.get('status')).toBe('AVAILABLE')
    expect(url.searchParams.get('sort')).toBe('id,desc')
  })

  it('says so when no dog looks for a sponsor', async () => {
    serve(() => jsonResponse(200, dogPage([])))

    renderApp()

    expect(await screen.findByText('No dog is looking for a sponsor right now. Please come back soon.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'See all dogs' })).toHaveAttribute('href', '/dogs')
  })

  it('shows the reason and tries again when the dogs cannot be loaded', async () => {
    let fail = true
    serve(() => (fail ? jsonResponse(400, { detail: 'Cannot list the dogs' }) : jsonResponse(200, dogPage([dog({ id: 5, name: 'Luna' })]))))

    renderApp()
    expect(await screen.findByText('Cannot list the dogs')).toBeInTheDocument()
    fail = false
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('link', { name: 'Luna' })).toBeInTheDocument()
  })
})

import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { ana } from '../test/users'
import { visit, visitPage } from '../test/visits'

const fetchMock = setUpFetchMock()

const session = {
  '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }),
  '/api/auth/me': () => jsonResponse(200, ana),
}

describe('MyVisitsPage', () => {
  it('lists the visits with the dog and the status', async () => {
    routeFetch(fetchMock, {
      ...session,
      '/api/visits/me': () =>
        jsonResponse(200, visitPage([visit({ id: 1, dogName: 'Rex' }), visit({ id: 2, dogId: 8, dogName: 'Luna', status: 'CANCELLED' })])),
    })

    renderApp('/visits')

    expect(await screen.findByRole('link', { name: 'Rex' })).toHaveAttribute('href', '/dogs/7')
    expect(screen.getByRole('link', { name: 'Luna' })).toHaveAttribute('href', '/dogs/8')
    expect(screen.getByText('Booked')).toBeInTheDocument()
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toContain('/api/visits/me?page=0&size=10')
  })

  it('says when there are no visits and links to the dogs', async () => {
    routeFetch(fetchMock, { ...session, '/api/visits/me': () => jsonResponse(200, visitPage([])) })

    renderApp('/visits')

    expect(await screen.findByText('You have not booked any visit yet.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Browse the dogs' })).toHaveAttribute('href', '/dogs')
  })

  it('offers to try again when the list cannot be loaded', async () => {
    let calls = 0
    routeFetch(fetchMock, {
      ...session,
      '/api/visits/me': () => {
        calls += 1
        return calls === 1 ? jsonResponse(500, { detail: 'Server error' }) : jsonResponse(200, visitPage([visit()]))
      },
    })

    renderApp('/visits')

    fireEvent.click(await screen.findByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('link', { name: 'Rex' })).toBeInTheDocument()
  })

  it('cancels a booked visit after a confirmation', async () => {
    let cancelled = false
    routeFetch(fetchMock, {
      ...session,
      '/api/visits/20/cancel': () => {
        cancelled = true
        return jsonResponse(200, visit({ id: 20, visitAt: '2099-12-01T09:00:00Z', status: 'CANCELLED' }))
      },
      '/api/visits/me': () =>
        jsonResponse(200, visitPage([visit({ id: 20, visitAt: '2099-12-01T09:00:00Z', status: cancelled ? 'CANCELLED' : 'BOOKED' })])),
    })

    renderApp('/visits')

    fireEvent.click(await screen.findByRole('button', { name: 'Cancel visit' }))
    expect(screen.getByText('Cancel this visit?')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Yes, cancel it' }))

    expect(await screen.findByText('Cancelled')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel visit' })).not.toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([url]) => String(url) === '/api/visits/20/cancel')
    expect(post?.[1]?.method).toBe('POST')
  })

  it('keeps the visit when the user changes their mind', async () => {
    routeFetch(fetchMock, {
      ...session,
      '/api/visits/me': () => jsonResponse(200, visitPage([visit({ id: 20, visitAt: '2099-12-01T09:00:00Z' })])),
    })

    renderApp('/visits')

    fireEvent.click(await screen.findByRole('button', { name: 'Cancel visit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Keep it' }))
    expect(screen.getByRole('button', { name: 'Cancel visit' })).toBeInTheDocument()
  })

  it('shows what the API says when a visit cannot be cancelled', async () => {
    routeFetch(fetchMock, {
      ...session,
      '/api/visits/20/cancel': () => jsonResponse(409, { detail: 'Only a booked visit can be cancelled' }),
      '/api/visits/me': () => jsonResponse(200, visitPage([visit({ id: 20, visitAt: '2099-12-01T09:00:00Z' })])),
    })

    renderApp('/visits')

    fireEvent.click(await screen.findByRole('button', { name: 'Cancel visit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, cancel it' }))
    expect(await screen.findByText('Only a booked visit can be cancelled')).toBeInTheDocument()
  })

  it('does not offer cancelling a visit that is over', async () => {
    routeFetch(fetchMock, {
      ...session,
      '/api/visits/me': () => jsonResponse(200, visitPage([visit({ id: 20, visitAt: '2020-01-01T09:00:00Z' })])),
    })

    renderApp('/visits')

    expect(await screen.findByRole('link', { name: 'Rex' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel visit' })).not.toBeInTheDocument()
  })

  it('shows the visits link in the menu of a user', async () => {
    routeFetch(fetchMock, { ...session, '/api/visits/me': () => jsonResponse(200, visitPage([])) })

    renderApp('/visits')

    expect(await screen.findByRole('link', { name: 'My visits' })).toHaveAttribute('href', '/visits')
  })
})

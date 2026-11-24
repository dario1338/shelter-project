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

  it('shows the visits link in the menu of a user', async () => {
    routeFetch(fetchMock, { ...session, '/api/visits/me': () => jsonResponse(200, visitPage([])) })

    renderApp('/visits')

    expect(await screen.findByRole('link', { name: 'My visits' })).toHaveAttribute('href', '/visits')
  })
})

import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { application, applicationPage } from '../test/applications'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { ana } from '../test/users'

const fetchMock = setUpFetchMock()

const session = {
  '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }),
  '/api/auth/me': () => jsonResponse(200, ana),
}

describe('MyApplicationsPage', () => {
  it('lists the applications with their status and notes', async () => {
    routeFetch(fetchMock, {
      ...session,
      '/api/sponsorships/me': () =>
        jsonResponse(200, applicationPage([
          application({ id: 1, dogName: 'Rex', status: 'REJECTED', userNote: 'Please', adminNote: 'Already sponsored' }),
          application({ id: 2, dogName: 'Luna', status: 'PENDING' }),
        ])),
    })

    renderApp('/applications')

    expect(await screen.findByRole('link', { name: 'Rex' })).toHaveAttribute('href', '/dogs/7')
    expect(screen.getByText('Rejected')).toBeInTheDocument()
    expect(screen.getByText('Your note: Please')).toBeInTheDocument()
    expect(screen.getByText('Shelter’s note: Already sponsored')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
  })

  it('offers cancelling only for a pending application', async () => {
    routeFetch(fetchMock, {
      ...session,
      '/api/sponsorships/me': () =>
        jsonResponse(200, applicationPage([application({ id: 1, status: 'APPROVED' }), application({ id: 2, status: 'PENDING' })])),
    })

    renderApp('/applications')
    await screen.findByText('Approved')

    expect(screen.getAllByRole('button', { name: 'Cancel application' })).toHaveLength(1)
  })

  it('asks for confirmation, cancels and shows the new status', async () => {
    let cancelled = false
    routeFetch(fetchMock, {
      ...session,
      '/api/sponsorships/2/cancel': () => {
        cancelled = true
        return jsonResponse(200, application({ id: 2, status: 'CANCELLED' }))
      },
      '/api/sponsorships/me': () =>
        jsonResponse(200, applicationPage([application({ id: 2, status: cancelled ? 'CANCELLED' : 'PENDING' })])),
    })

    renderApp('/applications')
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel application' }))
    expect(cancelled).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'Yes, cancel it' }))

    expect(await screen.findByText('Cancelled')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel application' })).not.toBeInTheDocument()
  })

  it('keeps the application when the user changes their mind', async () => {
    routeFetch(fetchMock, { ...session, '/api/sponsorships/me': () => jsonResponse(200, applicationPage([application()])) })

    renderApp('/applications')
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel application' }))
    fireEvent.click(screen.getByRole('button', { name: 'Keep it' }))

    expect(screen.getByRole('button', { name: 'Cancel application' })).toBeInTheDocument()
  })

  it('shows the reason when cancelling fails', async () => {
    routeFetch(fetchMock, {
      ...session,
      '/api/sponsorships/1/cancel': () => jsonResponse(409, { detail: 'Only a pending application can be changed' }),
      '/api/sponsorships/me': () => jsonResponse(200, applicationPage([application()])),
    })

    renderApp('/applications')
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel application' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, cancel it' }))

    expect(await screen.findByText('Only a pending application can be changed')).toBeInTheDocument()
  })

  it('says when there are no applications', async () => {
    routeFetch(fetchMock, { ...session, '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])) })

    renderApp('/applications')

    expect(await screen.findByText('You have not applied for any dog yet.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Browse available dogs' })).toHaveAttribute('href', '/dogs?status=AVAILABLE')
  })

  it('shows an error and can try again', async () => {
    let fail = true
    routeFetch(fetchMock, {
      ...session,
      '/api/sponsorships/me': () => (fail ? jsonResponse(500, { detail: 'Boom' }) : jsonResponse(200, applicationPage([application()]))),
    })

    renderApp('/applications')
    expect(await screen.findByText('Boom')).toBeInTheDocument()
    fail = false
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('link', { name: 'Rex' })).toBeInTheDocument()
  })

  it('asks for the page in the address', async () => {
    routeFetch(fetchMock, {
      ...session,
      '/api/sponsorships/me': () => jsonResponse(200, applicationPage([application()], { number: 1, totalPages: 2 })),
    })

    renderApp('/applications?page=2')

    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/sponsorships/me?page=1&size=10'))).toBe(true)
  })
})

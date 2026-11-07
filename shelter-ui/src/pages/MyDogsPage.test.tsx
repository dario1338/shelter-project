import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { application, applicationPage } from '../test/applications'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { admin, ana } from '../test/users'

const fetchMock = setUpFetchMock()

function session(user = ana) {
  return {
    '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }),
    '/api/auth/me': () => jsonResponse(200, user),
  }
}

describe('MyDogsPage', () => {
  it('lists the dogs of the approved applications, each with a way to its care details', async () => {
    routeFetch(fetchMock, {
      ...session(),
      '/api/sponsorships/me': () =>
        jsonResponse(200, applicationPage([
          application({ id: 1, dogId: 7, dogName: 'Rex', status: 'APPROVED', updatedAt: '2026-11-02T10:00:00Z' }),
          application({ id: 2, dogId: 8, dogName: 'Luna', status: 'APPROVED' }),
        ])),
    })

    renderApp('/my-dogs')

    expect(await screen.findByRole('link', { name: 'Rex' })).toHaveAttribute('href', '/dogs/7')
    expect(screen.getByText('You sponsor this dog since 2 Nov 2026')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Care details' })[0]).toHaveAttribute('href', '/dogs/7')
    expect(screen.getByRole('link', { name: 'Luna' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/sponsorships/me?page=0&size=10&status=APPROVED')).toBe(true)
  })

  it('says so when the user sponsors no dog', async () => {
    routeFetch(fetchMock, { ...session(), '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])) })

    renderApp('/my-dogs')

    expect(await screen.findByText('You do not sponsor any dog yet.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Browse available dogs' })).toBeInTheDocument()
  })

  it('shows an error and can try again', async () => {
    let fail = true
    routeFetch(fetchMock, {
      ...session(),
      '/api/sponsorships/me': () => (fail ? jsonResponse(500, { detail: 'Boom' }) : jsonResponse(200, applicationPage([application({ status: 'APPROVED' })]))),
    })

    renderApp('/my-dogs')
    expect(await screen.findByText('Boom')).toBeInTheDocument()
    fail = false
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('link', { name: 'Rex' })).toBeInTheDocument()
  })

  it('asks for the page given in the address and offers a way back from an empty one', async () => {
    routeFetch(fetchMock, { ...session(), '/api/sponsorships/me': () => jsonResponse(200, applicationPage([], { number: 4, totalPages: 1 })) })

    renderApp('/my-dogs?page=5')

    expect(await screen.findByText('This page does not exist.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Go to the first page' }))
    await screen.findByText('You do not sponsor any dog yet.')
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('page=0'))).toBe(true)
  })

  it('is a link in the header for a user, and not for an administrator', async () => {
    routeFetch(fetchMock, { ...session(), '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])) })
    const first = renderApp('/my-dogs')
    expect(await screen.findByRole('link', { name: 'My dogs' })).toHaveAttribute('href', '/my-dogs')
    first.unmount()

    routeFetch(fetchMock, session(admin))
    renderApp('/')
    await screen.findByRole('link', { name: 'Applications' })
    expect(screen.queryByRole('link', { name: 'My dogs' })).not.toBeInTheDocument()
  })

  it('is not for an administrator or a visitor', async () => {
    routeFetch(fetchMock, session(admin))
    const first = renderApp('/my-dogs')
    expect(await screen.findByRole('heading', { name: 'No access' })).toBeInTheDocument()
    first.unmount()

    routeFetch(fetchMock, {})
    renderApp('/my-dogs')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})

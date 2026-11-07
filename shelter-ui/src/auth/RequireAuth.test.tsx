import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { applicationPage } from '../test/applications'
import { renderApp } from '../test/renderApp'
import { admin, ana } from '../test/users'

const fetchMock = setUpFetchMock()

function signedInAs(user: typeof ana) {
  routeFetch(fetchMock, {
    '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }),
    '/api/auth/me': () => jsonResponse(200, user),
    '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])),
  })
}

describe('RequireAuth', () => {
  it('sends a visitor to sign in', async () => {
    routeFetch(fetchMock, {})

    renderApp('/applications')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('waits for the session to be restored before deciding', async () => {
    let answer: (response: Response) => void = () => undefined
    routeFetch(fetchMock, {
      '/api/auth/refresh': () => new Promise<Response>((resolve) => (answer = resolve)) as unknown as Response,
      '/api/auth/me': () => jsonResponse(200, ana),
      '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])),
    })

    renderApp('/applications')

    expect(await screen.findByText('Checking your session…')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument()
    answer(jsonResponse(200, { accessToken: 't1' }))
    expect(await screen.findByRole('heading', { name: 'My applications' })).toBeInTheDocument()
  })

  it('shows the page to a user', async () => {
    signedInAs(ana)

    renderApp('/applications')

    expect(await screen.findByRole('heading', { name: 'My applications' })).toBeInTheDocument()
  })

  it('does not show the page to an administrator', async () => {
    signedInAs(admin)

    renderApp('/applications')

    expect(await screen.findByRole('heading', { name: 'No access' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'My applications' })).not.toBeInTheDocument()
  })

  it('returns to the protected page after signing in', async () => {
    routeFetch(fetchMock, {
      '/api/auth/login': () => jsonResponse(200, { accessToken: 't1' }),
      '/api/auth/me': () => jsonResponse(200, ana),
      '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])),
    })
    renderApp('/applications')
    await screen.findByRole('heading', { name: 'Sign in' })

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ana@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret123' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form')!)

    expect(await screen.findByRole('heading', { name: 'My applications' })).toBeInTheDocument()
  })
})

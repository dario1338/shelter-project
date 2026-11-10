import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { applicationPage } from '../test/applications'
import { dogPage } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { admin, ana } from '../test/users'

const fetchMock = setUpFetchMock()

function serve(signedIn: typeof ana) {
  let user: typeof ana | null = signedIn
  routeFetch(fetchMock, {
    '/api/auth/refresh': () => (user ? jsonResponse(200, { accessToken: 't1' }) : new Response(null, { status: 401 })),
    '/api/auth/me': () => jsonResponse(200, user),
    '/api/auth/logout': () => {
      user = null
      return new Response(null, { status: 204 })
    },
    '/api/auth/login': (_url, init) => {
      user = JSON.parse(String(init?.body)).email === admin.email ? admin : ana
      return jsonResponse(200, { accessToken: 't2' })
    },
    '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])),
    '/api/sponsorships': () => jsonResponse(200, applicationPage([])),
    '/api/dogs': () => jsonResponse(200, dogPage([])),
  })
}

describe('signing out', () => {
  it('takes the user to the home page, not to the sign-in page of the page that was open', async () => {
    serve(ana)
    renderApp('/applications')
    await screen.findByRole('heading', { name: 'My applications' })

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: /Welcome to/ })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument()
  })

  it('lets an administrator sign in next and see the home page, not the page of the user before', async () => {
    serve(ana)
    renderApp('/applications')
    await screen.findByRole('heading', { name: 'My applications' })
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    await screen.findByRole('heading', { name: /Welcome to/ })

    fireEvent.click(screen.getByRole('link', { name: 'Sign in' }))
    fireEvent.change(await screen.findByLabelText('Email'), { target: { value: 'admin@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret123' } })
    fireEvent.submit(screen.getAllByRole('button', { name: 'Sign in' })[0].closest('form')!)

    expect(await screen.findByRole('heading', { name: /Welcome to/ })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'No access' })).not.toBeInTheDocument()
  })

  it('still sends a visitor who opens a page of a role to sign in, and back to it afterwards', async () => {
    serve(ana)
    // Nobody is signed in: the refresh is refused until the login call.
    routeFetch(fetchMock, {
      '/api/auth/refresh': () => new Response(null, { status: 401 }),
      '/api/auth/me': () => jsonResponse(200, ana),
      '/api/auth/login': () => jsonResponse(200, { accessToken: 't2' }),
      '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])),
      '/api/dogs': () => jsonResponse(200, dogPage([])),
    })
    renderApp('/applications')
    await screen.findByRole('heading', { name: 'Sign in' })

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ana@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret123' } })
    fireEvent.submit(screen.getAllByRole('button', { name: 'Sign in' })[0].closest('form')!)

    expect(await screen.findByRole('heading', { name: 'My applications' })).toBeInTheDocument()
  })
})

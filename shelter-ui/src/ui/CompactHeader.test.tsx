import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dog, dogPage } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { narrowScreen } from '../test/screen'
import { admin, ana } from '../test/users'

const fetchMock = setUpFetchMock()

function serve(user?: typeof ana) {
  routeFetch(fetchMock, {
    '/api/auth/refresh': () => (user ? jsonResponse(200, { accessToken: 't1' }) : new Response(null, { status: 401 })),
    '/api/auth/me': () => jsonResponse(200, user),
    '/api/auth/logout': () => new Response(null, { status: 204 }),
    '/api/dogs': () => jsonResponse(200, dogPage([dog({ id: 3, name: 'Luna' })])),
  })
}

const menuButton = () => screen.getByRole('button', { name: /the menu$/ })

describe('the header on a phone', () => {
  it('shows the menu button and the signed-in name, and keeps the links in the menu until it is opened', async () => {
    narrowScreen()
    serve(ana)

    renderApp()

    expect(await screen.findByText('Ana')).toBeInTheDocument()
    expect(menuButton()).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Switch to light theme' })).toBeInTheDocument()
  })

  it('opens the links of the role and the way to sign out', async () => {
    narrowScreen()
    serve(ana)
    renderApp()
    await screen.findByText('Ana')

    fireEvent.click(menuButton())

    const main = screen.getByRole('navigation', { name: 'Main' })
    expect(main).toHaveTextContent('DogsMy dogsMy applications')
    expect(menuButton()).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })

  it('gives an administrator the applications', async () => {
    narrowScreen()
    serve(admin)
    renderApp()
    await screen.findByText('Admin')

    fireEvent.click(menuButton())

    expect(screen.getByRole('link', { name: 'Applications' })).toHaveAttribute('href', '/admin/applications?status=PENDING')
    expect(screen.queryByRole('link', { name: 'My dogs' })).not.toBeInTheDocument()
  })

  it('offers sign in and create account to a visitor who is not signed in', async () => {
    narrowScreen()
    serve()
    renderApp()

    fireEvent.click(await screen.findByRole('button', { name: 'Open the menu' }))

    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: 'Create account' })).toHaveAttribute('href', '/register')
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })

  it('closes the menu when a link is used', async () => {
    narrowScreen()
    serve(ana)
    renderApp()
    await screen.findByText('Ana')

    fireEvent.click(menuButton())
    fireEvent.click(screen.getByRole('link', { name: 'Dogs' }))

    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Dogs' })).toBeInTheDocument()
  })

  it('closes the menu with the escape key', async () => {
    narrowScreen()
    serve(ana)
    renderApp()
    await screen.findByText('Ana')

    fireEvent.click(menuButton())
    fireEvent.keyDown(screen.getByRole('navigation', { name: 'Main' }), { key: 'Escape' })

    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
  })

  it('signs the user out from the menu', async () => {
    narrowScreen()
    serve(ana)
    renderApp()
    await screen.findByText('Ana')

    fireEvent.click(menuButton())
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('button', { name: 'Open the menu' })).toBeInTheDocument()
    expect(screen.queryByText('Ana')).not.toBeInTheDocument()
  })
})

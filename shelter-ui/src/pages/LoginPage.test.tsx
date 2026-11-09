import { fireEvent, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAccessToken } from '../auth/tokenStore'
import { dogPage } from '../test/dogs'
import { jsonResponse } from '../test/http'
import { renderApp } from '../test/renderApp'
import { ana } from '../test/users'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  clearAccessToken()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  // Opening the page first tries to restore a session; there is none.
  // The home page, where a signed-in visitor lands, lists the newest dogs.
  fetchMock.mockImplementation(async () => jsonResponse(200, dogPage([])))
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
})

afterEach(() => {
  vi.unstubAllGlobals()
})

async function openLoginPage() {
  renderApp('/login')
  await screen.findByRole('heading', { name: 'Sign in' })
}

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function submit() {
  fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form')!)
}

describe('LoginPage', () => {
  it('signs in and goes to the home page', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 't1' }))
      .mockResolvedValueOnce(jsonResponse(200, ana))
    await openLoginPage()

    fill('Email', ' ana@example.com ')
    fill('Password', 'secret123')
    submit()

    expect(await screen.findByRole('button', { name: 'Sign out' })).toBeInTheDocument()
    const loginCall = fetchMock.mock.calls[1]
    expect(loginCall[0]).toBe('/api/auth/login')
    expect(JSON.parse(loginCall[1]?.body as string)).toEqual({
      email: 'ana@example.com',
      password: 'secret123',
    })
  })

  it('explains what is missing without calling the server', async () => {
    await openLoginPage()

    submit()

    expect(await screen.findByText('Email is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('rejects an email address that is not plausible', async () => {
    await openLoginPage()

    fill('Email', 'not-an-email')
    fill('Password', 'secret123')
    submit()

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('shows the reason when the server refuses the credentials', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { detail: 'Invalid email or password' }))
    await openLoginPage()

    fill('Email', 'ana@example.com')
    fill('Password', 'wrong-password')
    submit()

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('tells the user to wait when the service is starting up', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>502</html>', { status: 502 }))
    await openLoginPage()

    fill('Email', 'ana@example.com')
    fill('Password', 'secret123')
    submit()

    expect(
      await screen.findByText('The service is temporarily unavailable. Please try again in a minute.'),
    ).toBeInTheDocument()
  })

  it('links to the registration page', async () => {
    await openLoginPage()

    expect(screen.getByRole('link', { name: 'Create one' })).toHaveAttribute('href', '/register')
  })
})

import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAccessToken } from '../auth/tokenStore'
import { jsonResponse } from '../test/http'
import { renderApp } from '../test/renderApp'
import { ana } from '../test/users'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  clearAccessToken()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
})

afterEach(() => {
  vi.unstubAllGlobals()
})

async function openRegisterPage() {
  renderApp('/register')
  await screen.findByRole('heading', { name: 'Create account' })
}

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function fillValidForm() {
  fill('First name', ' Ana ')
  fill('Last name', 'Anić')
  fill('Email', 'ana@example.com')
  fill('Password', 'secret123')
}

function submit() {
  fireEvent.submit(screen.getByRole('button', { name: 'Create account' }).closest('form')!)
}

describe('RegisterPage', () => {
  it('creates the account, signs the user in and goes to the home page', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(201, { id: 1, ...ana })) // register
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 't1' })) // login
      .mockResolvedValueOnce(jsonResponse(200, ana)) // /me
    await openRegisterPage()

    fillValidForm()
    submit()

    expect(await screen.findByText('Signed in as ana@example.com')).toBeInTheDocument()
    const [registerUrl, registerInit] = fetchMock.mock.calls[1]
    expect(registerUrl).toBe('/api/auth/register')
    expect(JSON.parse(registerInit?.body as string)).toEqual({
      firstName: 'Ana',
      lastName: 'Anić',
      email: 'ana@example.com',
      password: 'secret123',
    })
    expect(fetchMock.mock.calls[2][0]).toBe('/api/auth/login')
  })

  it('explains every invalid field without calling the server', async () => {
    await openRegisterPage()

    fill('Password', 'short')
    submit()

    expect(await screen.findByText('First name is required')).toBeInTheDocument()
    expect(screen.getByText('Last name is required')).toBeInTheDocument()
    expect(screen.getByText('Email is required')).toBeInTheDocument()
    expect(screen.getByText('Password must be at least 8 characters')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('shows the reason when the email is already used', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(409, { detail: 'Email is already in use' }))
    await openRegisterPage()

    fillValidForm()
    submit()

    expect(await screen.findByText('Email is already in use')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('keeps the user on the form when sign in after registration fails', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(201, { id: 1, ...ana }))
      .mockResolvedValueOnce(jsonResponse(429, { detail: 'Too many attempts' }))
    await openRegisterPage()

    fillValidForm()
    submit()

    expect(await screen.findByText('Too many attempts')).toBeInTheDocument()
  })

  it('links to the sign in page', async () => {
    await openRegisterPage()

    // The header has a sign in link as well, so look inside the page content.
    const link = within(screen.getByRole('main')).getByRole('link', { name: 'Sign in' })
    expect(link).toHaveAttribute('href', '/login')
  })
})

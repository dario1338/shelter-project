import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { application } from '../test/applications'
import { dog } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { admin, ana } from '../test/users'
import type { Dog } from '../api/dogs'
import type { CurrentUser } from '../auth/currentUser'

const fetchMock = setUpFetchMock()

function open(dogData: Dog, user: CurrentUser | null, extra: Record<string, () => Response> = {}) {
  routeFetch(fetchMock, {
    '/api/dogs/7': () => jsonResponse(200, dogData),
    ...(user
      ? { '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }), '/api/auth/me': () => jsonResponse(200, user) }
      : {}),
    ...extra,
  })
  renderApp('/dogs/7')
}

describe('SponsorshipSection', () => {
  it('invites a visitor to sign in and returns to the dog afterwards', async () => {
    open(dog({ id: 7 }), null)

    const link = await screen.findByRole('link', { name: 'Sign in' })
    fireEvent.click(link)
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('sends the application of a user and links to the list', async () => {
    open(dog({ id: 7, name: 'Rex' }), ana, {
      '/api/sponsorships': () => jsonResponse(201, application()),
    })

    fireEvent.change(await screen.findByLabelText(/Note for the shelter/), { target: { value: 'I would love to' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply for sponsorship' }))

    expect(await screen.findByText('Your application was sent.')).toBeInTheDocument()
    expect(within(screen.getByRole('status')).getByRole('link', { name: 'My applications' })).toHaveAttribute('href', '/applications')
    const post = fetchMock.mock.calls.find(([url]) => String(url) === '/api/sponsorships')
    expect(JSON.parse(post![1]?.body as string)).toEqual({ dogId: 7, note: 'I would love to' })
  })

  it('shows what the API says when the application is refused', async () => {
    open(dog({ id: 7 }), ana, {
      '/api/sponsorships': () => jsonResponse(409, { detail: 'You already have a pending application for this dog' }),
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Apply for sponsorship' }))

    expect(await screen.findByText('You already have a pending application for this dog')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Apply for sponsorship' })).toBeInTheDocument()
  })

  it('does not send a note that is too long', async () => {
    open(dog({ id: 7 }), ana)

    fireEvent.change(await screen.findByLabelText(/Note for the shelter/), { target: { value: 'a'.repeat(1001) } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply for sponsorship' }))

    expect(await screen.findByText('The note must be at most 1000 characters')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/sponsorships')).toBe(false)
  })

  it('offers nothing for a dog that is not available', async () => {
    open(dog({ id: 7, status: 'SPONSORED' }), ana)

    expect(await screen.findByText('This dog is not available for sponsorship.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Apply for sponsorship' })).not.toBeInTheDocument()
  })

  it('does not offer an administrator to apply', async () => {
    open(dog({ id: 7 }), admin)

    await screen.findByRole('heading', { name: 'Rex' })
    await screen.findByText('Admin')
    expect(screen.queryByRole('button', { name: 'Apply for sponsorship' })).not.toBeInTheDocument()
  })
})

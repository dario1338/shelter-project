import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Dog } from '../api/dogs'
import type { CurrentUser } from '../auth/currentUser'
import { applicationPage } from '../test/applications'
import { dog } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { admin, ana } from '../test/users'
import { visit } from '../test/visits'

const fetchMock = setUpFetchMock()

function open(dogData: Dog, user: CurrentUser | null, extra: Record<string, () => Response> = {}) {
  routeFetch(fetchMock, {
    '/api/dogs/7/care': () => jsonResponse(200, { sterilized: null, microchipNumber: null, healthNotes: null, internalNote: null, vaccinations: [], updatedAt: null }),
    '/api/dogs/7': () => jsonResponse(200, dogData),
    '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])),
    ...(user
      ? { '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }), '/api/auth/me': () => jsonResponse(200, user) }
      : {}),
    ...extra,
  })
  renderApp('/dogs/7')
}

describe('BookVisitSection', () => {
  it('books a visit for the chosen day and hour and links to the visits', async () => {
    open(dog({ id: 7, name: 'Rex' }), ana, {
      '/api/visits': () => jsonResponse(201, visit()),
    })

    fireEvent.change(await screen.findByLabelText('Day'), { target: { value: '2099-12-01' } })
    fireEvent.change(screen.getByLabelText('Time'), { target: { value: '11' } })
    fireEvent.click(screen.getByRole('button', { name: 'Book a visit' }))

    const status = await screen.findByText(/Your visit is booked for/)
    expect(within(status.parentElement!).getByRole('link', { name: 'My visits' })).toHaveAttribute('href', '/visits')
    const post = fetchMock.mock.calls.find(([url]) => String(url) === '/api/visits')
    expect(JSON.parse(post![1]?.body as string)).toEqual({ dogId: 7, date: '2099-12-01', hour: 11 })
  })

  it('asks for a day before it sends anything', async () => {
    open(dog({ id: 7 }), ana)

    fireEvent.click(await screen.findByRole('button', { name: 'Book a visit' }))

    expect(await screen.findByText('Choose a day')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/visits')).toBe(false)
  })

  it('shows what the API says when the time is taken', async () => {
    open(dog({ id: 7 }), ana, {
      '/api/visits': () => jsonResponse(409, { detail: 'This time is already booked for this dog' }),
    })

    fireEvent.change(await screen.findByLabelText('Day'), { target: { value: '2099-12-01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Book a visit' }))

    expect(await screen.findByText('This time is already booked for this dog')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Book a visit' })).toBeInTheDocument()
  })

  it('is not offered to a visitor who is not signed in', async () => {
    open(dog({ id: 7, name: 'Rex' }), null)
    expect(await screen.findByRole('heading', { level: 1, name: 'Rex' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Book a visit' })).not.toBeInTheDocument()
  })

  it('is not offered to an administrator', async () => {
    open(dog({ id: 7, name: 'Rex' }), admin)
    expect(await screen.findByRole('link', { name: 'Edit dog' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Book a visit' })).not.toBeInTheDocument()
  })

  it('is not offered for a dog that was adopted', async () => {
    open(dog({ id: 7, name: 'Rex', status: 'ADOPTED' }), ana)
    expect(await screen.findByText('This dog is not available for sponsorship.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Book a visit' })).not.toBeInTheDocument()
  })
})

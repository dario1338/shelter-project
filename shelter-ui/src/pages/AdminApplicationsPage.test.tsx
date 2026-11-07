import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { application, applicationPage } from '../test/applications'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { admin, ana } from '../test/users'

const fetchMock = setUpFetchMock()

function session(user = admin) {
  return {
    '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }),
    '/api/auth/me': () => jsonResponse(200, user),
  }
}

describe('AdminApplicationsPage', () => {
  it('is for administrators only', async () => {
    routeFetch(fetchMock, session(ana))

    renderApp('/admin/applications')

    expect(await screen.findByRole('heading', { name: 'No access' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/sponsorships'))).toBe(false)
  })

  it('sends a visitor to sign in', async () => {
    routeFetch(fetchMock, {})

    renderApp('/admin/applications')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('lists the applicants, their notes and the status', async () => {
    routeFetch(fetchMock, {
      ...session(),
      '/api/sponsorships': () =>
        jsonResponse(200, applicationPage([
          application({ id: 1, applicantName: 'Ana Anić', applicantEmail: 'ana@example.com', userNote: 'Please', status: 'PENDING' }),
          application({ id: 2, dogName: 'Luna', applicantName: 'Bob Bobić', applicantEmail: 'bob@example.com', status: 'REJECTED', adminNote: 'Too many dogs' }),
        ])),
    })

    renderApp('/admin/applications?status=PENDING')

    expect(await screen.findByText('Ana Anić (ana@example.com)')).toBeInTheDocument()
    expect(screen.getByText('Applicant’s note: Please')).toBeInTheDocument()
    expect(screen.getByText('Shelter’s note: Too many dogs')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Review' })).toHaveLength(1)
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/sponsorships?page=0&size=10&status=PENDING')).toBe(true)
  })

  it('changes the filter and the address', async () => {
    routeFetch(fetchMock, { ...session(), '/api/sponsorships': () => jsonResponse(200, applicationPage([])) })

    renderApp('/admin/applications')
    await screen.findByText('There are no applications here.')
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'APPROVED' } })

    await screen.findByText('There are no applications here.')
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('status=APPROVED'))).toBe(true)
  })

  it('approves with a note and refreshes the list', async () => {
    let approved = false
    routeFetch(fetchMock, {
      ...session(),
      '/api/sponsorships/1/approve': () => {
        approved = true
        return jsonResponse(200, application({ id: 1, status: 'APPROVED' }))
      },
      '/api/sponsorships': () =>
        jsonResponse(200, applicationPage([application({ id: 1, status: approved ? 'APPROVED' : 'PENDING' })])),
    })

    renderApp('/admin/applications')
    fireEvent.click(await screen.findByRole('button', { name: 'Review' }))
    fireEvent.change(screen.getByLabelText(/Note for the applicant/), { target: { value: ' Welcome ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }))

    expect(await screen.findByText('Approved', { ignore: 'option' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review' })).not.toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => String(url) === '/api/sponsorships/1/approve')
    expect(JSON.parse(call![1]?.body as string)).toEqual({ adminNote: 'Welcome' })
  })

  it('rejects without a note', async () => {
    routeFetch(fetchMock, {
      ...session(),
      '/api/sponsorships/1/reject': () => jsonResponse(200, application({ id: 1, status: 'REJECTED' })),
      '/api/sponsorships': () => jsonResponse(200, applicationPage([application({ id: 1 })])),
    })

    renderApp('/admin/applications')
    fireEvent.click(await screen.findByRole('button', { name: 'Review' }))
    fireEvent.click(screen.getByRole('button', { name: 'Reject' }))

    await screen.findByRole('button', { name: 'Review' })
    const call = fetchMock.mock.calls.find(([url]) => String(url) === '/api/sponsorships/1/reject')
    expect(JSON.parse(call![1]?.body as string)).toEqual({})
  })

  it('shows the reason when the decision is refused and keeps the panel open', async () => {
    routeFetch(fetchMock, {
      ...session(),
      '/api/sponsorships/1/approve': () => jsonResponse(409, { detail: 'Dog is no longer available for sponsorship' }),
      '/api/sponsorships': () => jsonResponse(200, applicationPage([application({ id: 1 })])),
    })

    renderApp('/admin/applications')
    fireEvent.click(await screen.findByRole('button', { name: 'Review' }))
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }))

    expect(await screen.findByText('Dog is no longer available for sponsorship')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument()
  })

  it('does not send a note that is too long', async () => {
    routeFetch(fetchMock, { ...session(), '/api/sponsorships': () => jsonResponse(200, applicationPage([application({ id: 1 })])) })

    renderApp('/admin/applications')
    fireEvent.click(await screen.findByRole('button', { name: 'Review' }))
    fireEvent.change(screen.getByLabelText(/Note for the applicant/), { target: { value: 'a'.repeat(1001) } })
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }))

    expect(await screen.findByText('The note must be at most 1000 characters')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/approve'))).toBe(false)
  })

  it('can close the review panel', async () => {
    routeFetch(fetchMock, { ...session(), '/api/sponsorships': () => jsonResponse(200, applicationPage([application({ id: 1 })])) })

    renderApp('/admin/applications')
    fireEvent.click(await screen.findByRole('button', { name: 'Review' }))
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))

    expect(screen.getByRole('button', { name: 'Review' })).toBeInTheDocument()
  })

  it('shows an error and can try again', async () => {
    let fail = true
    routeFetch(fetchMock, {
      ...session(),
      '/api/sponsorships': () => (fail ? jsonResponse(500, { detail: 'Boom' }) : jsonResponse(200, applicationPage([application()]))),
    })

    renderApp('/admin/applications')
    expect(await screen.findByText('Boom')).toBeInTheDocument()
    fail = false
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('link', { name: 'Rex' })).toBeInTheDocument()
  })

  it('shows the page asked for in the address', async () => {
    routeFetch(fetchMock, {
      ...session(),
      '/api/sponsorships': () => jsonResponse(200, applicationPage([application({ status: 'APPROVED' })], { number: 1, totalPages: 3 })),
    })

    renderApp('/admin/applications?page=2')

    expect(await screen.findByText('Page 2 of 3')).toBeInTheDocument()
  })
})

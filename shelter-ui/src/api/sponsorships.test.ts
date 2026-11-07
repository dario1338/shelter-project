import { describe, expect, it } from 'vitest'
import { setUpFetchMock } from '../test/fetchMock'
import { application, applicationPage } from '../test/applications'
import { jsonResponse } from '../test/http'
import { ApiError } from './ApiError'
import {
  applyForSponsorship,
  cancelApplication,
  decideApplication,
  fetchApplications,
  fetchMyApplications,
} from './sponsorships'

const fetchMock = setUpFetchMock()

describe('sponsorships api', () => {
  it('asks for one page of the own applications', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, applicationPage([application()])))

    const page = await fetchMyApplications({ page: 2 })

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/sponsorships/me?page=2&size=10')
    expect(page.content).toHaveLength(1)
  })

  it('passes the dog and status filters', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, applicationPage([])))

    await fetchMyApplications({ page: 0, size: 1, dogId: 7, status: 'PENDING' })

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/sponsorships/me?page=0&size=1&dogId=7&status=PENDING')
  })

  it('asks administrators for one page of all applications, optionally of one status', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, applicationPage([]))))

    await fetchApplications({ page: 1 })
    await fetchApplications({ page: 0, status: 'APPROVED' })

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/sponsorships?page=1&size=10')
    expect(String(fetchMock.mock.calls[1][0])).toBe('/api/sponsorships?page=0&size=10&status=APPROVED')
  })

  it('sends a decision with the trimmed note, or without a note', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, application({ status: 'APPROVED' }))))

    await decideApplication(4, 'approve', '  Welcome ')
    await decideApplication(5, 'reject', '  ')

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/sponsorships/4/approve')
    expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string)).toEqual({ adminNote: 'Welcome' })
    expect(String(fetchMock.mock.calls[1][0])).toBe('/api/sponsorships/5/reject')
    expect(JSON.parse(fetchMock.mock.calls[1][1]?.body as string)).toEqual({})
  })

  it('sends the dog and the trimmed note', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(201, application()))

    await applyForSponsorship(7, '  I love dogs ')

    const init = fetchMock.mock.calls[0][1]
    expect(init?.method).toBe('POST')
    expect(JSON.parse(init?.body as string)).toEqual({ dogId: 7, note: 'I love dogs' })
  })

  it('leaves the note out when it is empty', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(201, application()))

    await applyForSponsorship(7, '   ')

    expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string)).toEqual({ dogId: 7 })
  })

  it('throws the reason the API gives', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(409, { detail: 'Dog is not available for sponsorship' }))

    await expect(applyForSponsorship(7, '')).rejects.toMatchObject({
      status: 409,
      message: 'Dog is not available for sponsorship',
    })
    expect(ApiError).toBeDefined()
  })

  it('cancels an application', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, application({ status: 'CANCELLED' })))

    const result = await cancelApplication(4)

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/sponsorships/4/cancel')
    expect(result.status).toBe('CANCELLED')
  })
})

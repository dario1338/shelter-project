import { describe, expect, it } from 'vitest'
import { setUpFetchMock } from '../test/fetchMock'
import { application, applicationPage } from '../test/applications'
import { jsonResponse } from '../test/http'
import { ApiError } from './ApiError'
import { applyForSponsorship, cancelApplication, fetchMyApplications } from './sponsorships'

const fetchMock = setUpFetchMock()

describe('sponsorships api', () => {
  it('asks for one page of the own applications', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, applicationPage([application()])))

    const page = await fetchMyApplications(2)

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/sponsorships/me?page=2&size=10')
    expect(page.content).toHaveLength(1)
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

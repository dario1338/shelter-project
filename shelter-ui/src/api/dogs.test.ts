import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAccessToken } from '../auth/tokenStore'
import { dog, dogPage } from '../test/dogs'
import { jsonResponse } from '../test/http'
import { ApiError } from './ApiError'
import { fetchDog, fetchDogs } from './dogs'

const fetchMock = vi.fn<typeof fetch>()

function requestedUrl(): URL {
  return new URL(String(fetchMock.mock.calls[0][0]), 'http://localhost')
}

beforeEach(() => {
  clearAccessToken()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('fetchDogs', () => {
  it('asks for one page in a fixed order, so pages never overlap', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, dogPage([dog()])))

    await fetchDogs({ page: 2 })

    const url = requestedUrl()
    expect(url.pathname).toBe('/api/dogs')
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.get('size')).toBe('12')
    expect(url.searchParams.getAll('sort')).toEqual(['name,asc', 'id,asc'])
    expect(url.searchParams.has('status')).toBe(false)
  })

  it('filters by status when one is given', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, dogPage([])))

    await fetchDogs({ page: 0, status: 'ADOPTED' })

    expect(requestedUrl().searchParams.get('status')).toBe('ADOPTED')
  })

  it('returns the page the API sent', async () => {
    const body = dogPage([dog({ id: 5, name: 'Luna' })], { totalPages: 3 })
    fetchMock.mockResolvedValueOnce(jsonResponse(200, body))

    await expect(fetchDogs({ page: 0 })).resolves.toEqual(body)
  })

  it('turns a failed answer into an ApiError with the message of the API', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(400, { detail: 'Invalid page' }))

    await expect(fetchDogs({ page: 0 })).rejects.toMatchObject({ status: 400, message: 'Invalid page' })
  })
})

describe('fetchDog', () => {
  it('reads one dog', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, dog({ id: 7 })))

    await expect(fetchDog(7)).resolves.toMatchObject({ id: 7, name: 'Rex' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/dogs/7')
  })

  it('rejects with an ApiError when the dog does not exist', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(404, { detail: 'Dog not found' }))

    const failure = fetchDog(99)

    await expect(failure).rejects.toBeInstanceOf(ApiError)
    await expect(failure).rejects.toMatchObject({ status: 404 })
  })
})

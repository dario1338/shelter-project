import { describe, expect, it } from 'vitest'
import { dog } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { care } from '../test/care'
import { jsonResponse } from '../test/http'
import { fetchCare, saveCare } from './care'
import { createDog, deleteDog, updateDog, type DogInput } from './dogs'

const fetchMock = setUpFetchMock()

const input: DogInput = { name: 'Rex', breed: null, birthYear: null, description: null, status: 'AVAILABLE' }

describe('writing dogs', () => {
  it('creates a dog', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(201, dog({ id: 9 })))

    const created = await createDog(input)

    expect(created.id).toBe(9)
    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/dogs')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('POST')
    expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string)).toEqual(input)
  })

  it('updates a dog with all its values, the missing ones as null', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, dog()))

    await updateDog(4, input)

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/dogs/4')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('PUT')
    expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string)).toHaveProperty('breed', null)
  })

  it('deletes a dog', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await deleteDog(4)

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/dogs/4')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('DELETE')
  })

  it.each([
    ['create', () => createDog(input)],
    ['update', () => updateDog(4, input)],
    ['delete', () => deleteDog(4)],
    ['read care', () => fetchCare(4)],
    ['save care', () => saveCare(4, { sterilized: null, microchipNumber: null, healthNotes: null, internalNote: null, vaccinations: [] })],
  ])('throws the reason the API gives when it refuses to %s', async (_name, action) => {
    fetchMock.mockResolvedValueOnce(jsonResponse(403, { detail: 'Not allowed' }))

    await expect(action()).rejects.toMatchObject({ status: 403, message: 'Not allowed' })
  })
})

describe('care api', () => {
  it('reads the care details of a dog', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, care()))

    const result = await fetchCare(4)

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/dogs/4/care')
    expect(result.microchipNumber).toBe('900123')
  })

  it('saves the care details of a dog', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, care()))

    await saveCare(4, { sterilized: true, microchipNumber: null, healthNotes: null, internalNote: null, vaccinations: [] })

    expect(fetchMock.mock.calls[0][1]?.method).toBe('PUT')
    expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string)).toHaveProperty('sterilized', true)
  })
})

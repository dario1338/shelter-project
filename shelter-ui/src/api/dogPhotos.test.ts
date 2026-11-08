import { describe, expect, it } from 'vitest'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse } from '../test/http'
import { ApiError } from './ApiError'
import { deleteDogPhoto, uploadDogPhoto } from './dogPhotos'

const fetchMock = setUpFetchMock()

const file = new File(['bytes'], 'rex.jpg', { type: 'image/jpeg' })

describe('uploadDogPhoto', () => {
  it('sends the file as a form to the photos of the dog', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(201, { id: 5, url: 'https://files/a.jpg' }))

    const photo = await uploadDogPhoto(4, file)

    expect(photo).toEqual({ id: 5, url: 'https://files/a.jpg' })
    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('/api/dogs/4/images')
    expect(init?.method).toBe('POST')
    expect((init?.body as FormData).get('file')).toBeInstanceOf(File)
    expect(((init?.body as FormData).get('file') as File).name).toBe('rex.jpg')
  })

  it('leaves the content type to the browser, which adds the boundary of the form', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(201, { id: 5, url: 'https://files/a.jpg' }))

    await uploadDogPhoto(4, file)

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
    expect(headers.has('Content-Type')).toBe(false)
  })

  it('reports what the API says when it refuses the photo', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(400, { detail: 'The file is not a valid JPEG or PNG image' }))

    await expect(uploadDogPhoto(4, file)).rejects.toMatchObject({ status: 400, message: 'The file is not a valid JPEG or PNG image' })
  })

  it('explains a body that is too large even when the answer is not from the API', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>413 Request Entity Too Large</html>', { status: 413 }))

    const error = await uploadDogPhoto(4, file).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).message).toBe('The photo is too large (at most 5 MB)')
  })
})

describe('deleteDogPhoto', () => {
  it('deletes the photo of the dog', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await deleteDogPhoto(4, 9)

    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/dogs/4/images/9')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('DELETE')
  })

  it('reports a photo that does not exist', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(404, { detail: 'Image with id 9 not found for dog 4' }))

    await expect(deleteDogPhoto(4, 9)).rejects.toMatchObject({ status: 404 })
  })
})

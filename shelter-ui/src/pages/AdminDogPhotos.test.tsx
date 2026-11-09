import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Dog, DogPhoto } from '../api/dogs'
import { noCare } from '../test/care'
import { dog } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { admin } from '../test/users'

const fetchMock = setUpFetchMock()

const photo = (id: number): DogPhoto => ({ id, url: `https://files/${id}.jpg` })

function jpeg(name: string): File {
  return new File(['bytes'], name, { type: 'image/jpeg' })
}

// A dog whose photos change as the page uploads and deletes them, the way the API would answer.
function serveDog(initial: DogPhoto[], answers: { upload?: (name: string) => Response } = {}) {
  let photos = [...initial]
  let nextId = 100
  const current = (): Dog => dog({ id: 4, name: 'Rex', images: photos, primaryImageUrl: photos[0]?.url ?? null })
  routeFetch(fetchMock, {
    '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }),
    '/api/auth/me': () => jsonResponse(200, admin),
    '/api/dogs/4/images/': (url, init) => {
      if (init?.method === 'DELETE') {
        photos = photos.filter((item) => item.id !== Number(url.split('/').pop()))
      }
      return new Response(null, { status: 204 })
    },
    '/api/dogs/4/images': (_url, init) => {
      const name = ((init?.body as FormData).get('file') as File).name
      if (answers.upload) {
        const response = answers.upload(name)
        if (!response.ok) {
          return response
        }
      }
      const added = photo(nextId++)
      photos = [...photos, added]
      return jsonResponse(201, added)
    },
    '/api/dogs/4/care': () => jsonResponse(200, noCare),
    '/api/dogs/4': () => jsonResponse(200, current()),
  })
}

function calls(method: string, part: string): number {
  return fetchMock.mock.calls.filter(([url, init]) => String(url).includes(part) && init?.method === method).length
}

function choose(files: File[]) {
  fireEvent.change(screen.getByLabelText('Add photos'), { target: { files } })
}

describe('photos on the edit page', () => {
  it('shows the photos of the dog, the first as the main one', async () => {
    serveDog([photo(11), photo(12)])

    renderApp('/admin/dogs/4/edit?tab=photos')

    expect(await screen.findByRole('img', { name: 'Photo 1 of Rex' })).toHaveAttribute('src', 'https://files/11.jpg')
    expect(screen.getByRole('img', { name: 'Photo 2 of Rex' })).toBeInTheDocument()
    expect(screen.getAllByText('Main photo')).toHaveLength(1)
    expect(screen.getByText('Photo 2')).toBeInTheDocument()
  })

  it('says when the dog has no photos', async () => {
    serveDog([])

    renderApp('/admin/dogs/4/edit?tab=photos')

    expect(await screen.findByText('This dog has no photos yet.')).toBeInTheDocument()
  })

  it('uploads the chosen files one after another and shows them', async () => {
    serveDog([photo(11)])

    renderApp('/admin/dogs/4/edit?tab=photos')
    await screen.findByRole('img', { name: 'Photo 1 of Rex' })
    choose([jpeg('a.jpg'), jpeg('b.jpg')])

    expect(await screen.findByText('2 photos added.')).toBeInTheDocument()
    expect(calls('POST', '/api/dogs/4/images')).toBe(2)
    expect(await screen.findByRole('img', { name: 'Photo 3 of Rex' })).toBeInTheDocument()
  })

  it('does nothing when no file is chosen', async () => {
    serveDog([])

    renderApp('/admin/dogs/4/edit?tab=photos')
    await screen.findByText('This dog has no photos yet.')
    choose([])

    expect(calls('POST', '/api/dogs/4/images')).toBe(0)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('does not send a file that is not a JPEG or PNG image', async () => {
    serveDog([])

    renderApp('/admin/dogs/4/edit?tab=photos')
    await screen.findByText('This dog has no photos yet.')
    choose([new File(['x'], 'notes.txt', { type: 'text/plain' })])

    expect(await screen.findByText('notes.txt is not a JPEG or PNG image.')).toBeInTheDocument()
    expect(calls('POST', '/api/dogs/4/images')).toBe(0)
  })

  it('does not send a file over 5 MB', async () => {
    serveDog([])
    const big = jpeg('big.jpg')
    Object.defineProperty(big, 'size', { value: 6 * 1024 * 1024 })

    renderApp('/admin/dogs/4/edit?tab=photos')
    await screen.findByText('This dog has no photos yet.')
    choose([big])

    expect(await screen.findByText('big.jpg is too large (at most 5 MB).')).toBeInTheDocument()
    expect(calls('POST', '/api/dogs/4/images')).toBe(0)
  })

  it('does not send more photos than the dog can have', async () => {
    serveDog([photo(1), photo(2), photo(3), photo(4), photo(5)])

    renderApp('/admin/dogs/4/edit?tab=photos')
    await screen.findByRole('img', { name: 'Photo 5 of Rex' })
    choose([jpeg('a.jpg'), jpeg('b.jpg')])

    expect(await screen.findByText('Only 1 more photo can be added (at most 6 per dog).')).toBeInTheDocument()
    expect(calls('POST', '/api/dogs/4/images')).toBe(0)
  })

  it('offers no upload when the dog has the most photos it can have', async () => {
    serveDog([photo(1), photo(2), photo(3), photo(4), photo(5), photo(6)])

    renderApp('/admin/dogs/4/edit?tab=photos')

    expect(await screen.findByText(/has the most photos it can have/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Add photos')).not.toBeInTheDocument()
  })

  it('stops at the first refusal and says what was added before it', async () => {
    serveDog([], { upload: (name) => (name === 'bad.jpg' ? jsonResponse(400, { detail: 'The file is not a valid JPEG or PNG image' }) : jsonResponse(201, {})) })

    renderApp('/admin/dogs/4/edit?tab=photos')
    await screen.findByText('This dog has no photos yet.')
    choose([jpeg('good.jpg'), jpeg('bad.jpg'), jpeg('later.jpg')])

    expect(await screen.findByText('1 photo added. The file is not a valid JPEG or PNG image')).toBeInTheDocument()
    expect(calls('POST', '/api/dogs/4/images')).toBe(2)
  })

  it('asks before it deletes a photo, and deletes it when confirmed', async () => {
    serveDog([photo(11), photo(12)])

    renderApp('/admin/dogs/4/edit?tab=photos')
    fireEvent.click(await screen.findByRole('button', { name: 'Delete photo 2' }))
    expect(screen.getByText('Delete this photo?')).toBeInTheDocument()
    expect(calls('DELETE', '/api/dogs/4/images/12')).toBe(0)
    fireEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))

    await waitFor(() => expect(screen.queryByRole('img', { name: 'Photo 2 of Rex' })).not.toBeInTheDocument())
    expect(calls('DELETE', '/api/dogs/4/images/12')).toBe(1)
    expect(screen.getByRole('img', { name: 'Photo 1 of Rex' })).toBeInTheDocument()
  })

  it('keeps the photo when the administrator changes their mind', async () => {
    serveDog([photo(11)])

    renderApp('/admin/dogs/4/edit?tab=photos')
    fireEvent.click(await screen.findByRole('button', { name: 'Delete photo 1' }))
    fireEvent.click(screen.getByRole('button', { name: 'Keep it' }))

    expect(screen.getByRole('button', { name: 'Delete photo 1' })).toBeInTheDocument()
    expect(calls('DELETE', '/api/dogs/4/images/11')).toBe(0)
  })

  it('shows why a photo could not be deleted', async () => {
    serveDog([photo(11)])
    routeFetch(fetchMock, {
      '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }),
      '/api/auth/me': () => jsonResponse(200, admin),
      '/api/dogs/4/images/': () => jsonResponse(404, { detail: 'Image with id 11 not found for dog 4' }),
      '/api/dogs/4/care': () => jsonResponse(200, noCare),
      '/api/dogs/4': () => jsonResponse(200, dog({ id: 4, name: 'Rex', images: [photo(11)], primaryImageUrl: photo(11).url })),
    })

    renderApp('/admin/dogs/4/edit?tab=photos')
    fireEvent.click(await screen.findByRole('button', { name: 'Delete photo 1' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(await screen.findByText('Image with id 11 not found for dog 4')).toBeInTheDocument()
  })
})

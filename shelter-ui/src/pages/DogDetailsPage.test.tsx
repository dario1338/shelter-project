import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dog, dogPage } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'

const fetchMock = setUpFetchMock()

function requestedUrls(): string[] {
  return fetchMock.mock.calls.map(([input]) => String(input)).filter((url) => url.startsWith('/api/dogs'))
}

describe('DogDetailsPage', () => {
  it('shows the dog', async () => {
    routeFetch(fetchMock, { '/api/dogs/7': () => jsonResponse(200, dog({ id: 7, name: 'Rex', breed: 'Labrador', birthYear: 2020, description: 'Loves water.', status: 'SPONSORED' })) })

    renderApp('/dogs/7')

    expect(await screen.findByRole('heading', { name: 'Rex' })).toBeInTheDocument()
    expect(screen.getByText('Sponsored')).toBeInTheDocument()
    expect(screen.getByText('Labrador')).toBeInTheDocument()
    expect(screen.getByText(/\d+ years? \(born 2020\)/)).toBeInTheDocument()
    expect(screen.getByText('Loves water.')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'No photo of Rex yet' })).toBeInTheDocument()
  })

  it('shows the photos of the dog and lets the visitor choose between them', async () => {
    const images = [
      { id: 1, url: 'https://files/a.jpg' },
      { id: 2, url: 'https://files/b.jpg' },
    ]
    routeFetch(fetchMock, { '/api/dogs/7': () => jsonResponse(200, dog({ id: 7, name: 'Rex', images, primaryImageUrl: images[0].url })) })

    renderApp('/dogs/7')
    expect(await screen.findByRole('img', { name: 'Photo of Rex' })).toHaveAttribute('src', 'https://files/a.jpg')
    fireEvent.click(screen.getByRole('button', { name: 'Show photo 2 of 2' }))

    expect(screen.getByRole('img', { name: 'Photo of Rex' })).toHaveAttribute('src', 'https://files/b.jpg')
  })

  it('leaves out what is not known about the dog', async () => {
    routeFetch(fetchMock, { '/api/dogs/7': () => jsonResponse(200, dog({ id: 7, breed: null, birthYear: null, description: null })) })

    renderApp('/dogs/7')
    await screen.findByRole('heading', { name: 'Rex' })

    expect(screen.queryByText('Breed')).not.toBeInTheDocument()
    expect(screen.queryByText('Age')).not.toBeInTheDocument()
  })

  it('shows a loading state until the dog arrives', async () => {
    routeFetch(fetchMock, { '/api/dogs/7': () => new Promise<Response>(() => undefined) as unknown as Response })

    renderApp('/dogs/7')

    expect(await screen.findByRole('status')).toHaveTextContent('Loading the dog…')
  })

  it('says the dog was not found on 404', async () => {
    routeFetch(fetchMock, { '/api/dogs/99': () => jsonResponse(404, { detail: 'Dog not found' }) })

    renderApp('/dogs/99')

    expect(await screen.findByRole('heading', { name: 'Dog not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to all dogs' })).toHaveAttribute('href', '/dogs')
  })

  it.each(['abc', '0', '-3', '1.5'])('does not ask the API for the address /dogs/%s', async (id) => {
    routeFetch(fetchMock, {})

    renderApp(`/dogs/${id}`)

    expect(await screen.findByRole('heading', { name: 'Dog not found' })).toBeInTheDocument()
    expect(requestedUrls()).toEqual([])
  })

  it('explains a failure and tries again on request', async () => {
    let calls = 0
    routeFetch(fetchMock, {
      '/api/dogs/7': () => (++calls === 1 ? new Response('', { status: 503 }) : jsonResponse(200, dog({ id: 7 }))),
    })

    renderApp('/dogs/7')

    expect(await screen.findByText('The service is temporarily unavailable. Please try again in a minute.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Rex' })).toBeInTheDocument()
  })

  it('goes back to the list the user came from, with its filter and page', async () => {
    routeFetch(fetchMock, {
      '/api/dogs/4': () => jsonResponse(200, dog({ id: 4, name: 'Bella', status: 'ADOPTED' })),
      '/api/dogs?': () => jsonResponse(200, dogPage([dog({ id: 4, name: 'Bella', status: 'ADOPTED' })], { number: 1, totalPages: 2 })),
    })

    renderApp('/dogs?status=ADOPTED&page=2')
    fireEvent.click(await screen.findByRole('link', { name: 'Bella' }))

    await screen.findByRole('heading', { name: 'Bella' })
    expect(screen.getByRole('link', { name: '← All dogs' })).toHaveAttribute('href', '/dogs?status=ADOPTED&page=2')
  })

  it('goes back to the plain list when the dog was opened directly', async () => {
    routeFetch(fetchMock, { '/api/dogs/7': () => jsonResponse(200, dog({ id: 7 })) })

    renderApp('/dogs/7')
    await screen.findByRole('heading', { name: 'Rex' })

    expect(screen.getByRole('link', { name: '← All dogs' })).toHaveAttribute('href', '/dogs')
  })
})

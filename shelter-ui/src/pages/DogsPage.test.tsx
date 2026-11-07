import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dog, dogPage } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'

const fetchMock = setUpFetchMock()

function dogRequests(): URL[] {
  return fetchMock.mock.calls
    .map(([input]) => String(input))
    .filter((url) => url.startsWith('/api/dogs'))
    .map((url) => new URL(url, 'http://localhost'))
}

describe('DogsPage', () => {
  it('lists the dogs with their details and a link to each', async () => {
    routeFetch(fetchMock, {
      '/api/dogs': () =>
        jsonResponse(200, dogPage([dog({ id: 1, name: 'Rex', breed: 'Labrador', birthYear: 2020 }), dog({ id: 2, name: 'Luna', breed: null, birthYear: null, status: 'ADOPTED' })])),
    })

    renderApp('/dogs')

    const rex = await screen.findByRole('link', { name: 'Rex' })
    expect(rex).toHaveAttribute('href', '/dogs/1')
    expect(screen.getByRole('link', { name: 'Luna' })).toHaveAttribute('href', '/dogs/2')
    expect(screen.getByText(/Labrador · \d+ years?/)).toBeInTheDocument()
    // The same words are also options of the filter, so only the badges are looked at here.
    expect(screen.getByText('Adopted', { ignore: 'option' })).toBeInTheDocument()
    expect(screen.getByText('Available', { ignore: 'option' })).toBeInTheDocument()
  })

  it('asks for the first page of all dogs by default', async () => {
    routeFetch(fetchMock, { '/api/dogs': () => jsonResponse(200, dogPage([dog()])) })

    renderApp('/dogs')
    await screen.findByRole('link', { name: 'Rex' })

    const [request] = dogRequests()
    expect(request.searchParams.get('page')).toBe('0')
    expect(request.searchParams.has('status')).toBe(false)
  })

  it('shows a loading state until the dogs arrive', async () => {
    routeFetch(fetchMock, { '/api/dogs': () => new Promise<Response>(() => undefined) as unknown as Response })

    renderApp('/dogs')

    expect(await screen.findByRole('status')).toHaveTextContent('Loading dogs…')
  })

  it('filters by status and goes back to the first page', async () => {
    routeFetch(fetchMock, {
      '/api/dogs': (url) =>
        jsonResponse(200, new URL(url, 'http://localhost').searchParams.get('status') ? dogPage([dog({ id: 9, name: 'Old Max', status: 'ADOPTED' })]) : dogPage([dog()], { totalPages: 3 })),
    })

    renderApp('/dogs?page=2')
    await screen.findByRole('link', { name: 'Rex' })

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'ADOPTED' } })

    expect(await screen.findByRole('link', { name: 'Old Max' })).toBeInTheDocument()
    const last = dogRequests().at(-1)!
    expect(last.searchParams.get('status')).toBe('ADOPTED')
    expect(last.searchParams.get('page')).toBe('0')
  })

  it('moves between pages', async () => {
    routeFetch(fetchMock, {
      '/api/dogs': (url) => {
        const page = Number(new URL(url, 'http://localhost').searchParams.get('page'))
        return jsonResponse(200, dogPage([dog({ id: page + 1, name: `Dog ${page + 1}` })], { number: page, totalPages: 3 }))
      },
    })

    renderApp('/dogs')
    await screen.findByRole('link', { name: 'Dog 1' })
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByRole('link', { name: 'Dog 2' })).toBeInTheDocument()
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
    expect(dogRequests().at(-1)!.searchParams.get('page')).toBe('1')

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByRole('link', { name: 'Dog 3' })
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Previous' }))
    expect(await screen.findByRole('link', { name: 'Dog 2' })).toBeInTheDocument()
  })

  it('shows no page controls when everything fits on one page', async () => {
    routeFetch(fetchMock, { '/api/dogs': () => jsonResponse(200, dogPage([dog()])) })

    renderApp('/dogs')
    await screen.findByRole('link', { name: 'Rex' })

    expect(screen.queryByRole('navigation', { name: 'Pages' })).not.toBeInTheDocument()
  })

  it('reads the filter and the page from the address', async () => {
    routeFetch(fetchMock, { '/api/dogs': () => jsonResponse(200, dogPage([dog()], { number: 1, totalPages: 2 })) })

    renderApp('/dogs?status=SPONSORED&page=2')
    await screen.findByRole('link', { name: 'Rex' })

    const [request] = dogRequests()
    expect(request.searchParams.get('status')).toBe('SPONSORED')
    expect(request.searchParams.get('page')).toBe('1')
    expect(screen.getByLabelText('Status')).toHaveValue('SPONSORED')
  })

  it('ignores an address it does not understand', async () => {
    routeFetch(fetchMock, { '/api/dogs': () => jsonResponse(200, dogPage([dog()])) })

    renderApp('/dogs?page=abc&status=LOST')
    await screen.findByRole('link', { name: 'Rex' })

    const [request] = dogRequests()
    expect(request.searchParams.get('page')).toBe('0')
    expect(request.searchParams.has('status')).toBe(false)
  })

  it('says so when there are no dogs', async () => {
    routeFetch(fetchMock, { '/api/dogs': () => jsonResponse(200, dogPage([])) })

    renderApp('/dogs')

    expect(await screen.findByText('There are no dogs yet.')).toBeInTheDocument()
  })

  it('says so when no dog has the chosen status', async () => {
    routeFetch(fetchMock, { '/api/dogs': () => jsonResponse(200, dogPage([])) })

    renderApp('/dogs?status=SPONSORED')

    expect(await screen.findByText('There are no sponsored dogs right now.')).toBeInTheDocument()
  })

  it('offers the first page when the page in the address does not exist', async () => {
    routeFetch(fetchMock, {
      '/api/dogs': (url) =>
        jsonResponse(200, new URL(url, 'http://localhost').searchParams.get('page') === '0' ? dogPage([dog()]) : dogPage([], { number: 8, totalPages: 1 })),
    })

    renderApp('/dogs?page=9')
    expect(await screen.findByText('This page does not exist.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Go to the first page' }))
    expect(await screen.findByRole('link', { name: 'Rex' })).toBeInTheDocument()
  })

  it('explains a failure and tries again on request', async () => {
    let calls = 0
    routeFetch(fetchMock, {
      '/api/dogs': () => (++calls === 1 ? new Response('<html>bad gateway</html>', { status: 502 }) : jsonResponse(200, dogPage([dog()]))),
    })

    renderApp('/dogs')

    const message = 'The service is temporarily unavailable. Please try again in a minute.'
    expect(await screen.findByText(message)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('link', { name: 'Rex' })).toBeInTheDocument()
    expect(screen.queryByText(message)).not.toBeInTheDocument()
  })
})

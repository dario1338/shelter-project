import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { application, applicationPage } from '../test/applications'
import { care, noCare } from '../test/care'
import { dog, dogPage } from '../test/dogs'
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

function bodyOf(urlPart: string, method: string): unknown {
  const call = fetchMock.mock.calls.find(([url, init]) => String(url).includes(urlPart) && init?.method === method)
  return JSON.parse(call![1]?.body as string)
}

describe('adding a dog', () => {
  it('is for administrators only', async () => {
    routeFetch(fetchMock, session(ana))

    renderApp('/admin/dogs/new')

    expect(await screen.findByRole('heading', { name: 'No access' })).toBeInTheDocument()
  })

  it('checks the form before it sends anything', async () => {
    routeFetch(fetchMock, session())

    renderApp('/admin/dogs/new')
    fireEvent.click(await screen.findByRole('button', { name: 'Add dog' }))

    expect(await screen.findByText('Enter the name')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/dogs' && init?.method === 'POST')).toBe(false)
  })

  it('creates the dog and moves on to its edit page, where the care details can be added', async () => {
    routeFetch(fetchMock, {
      ...session(),
      '/api/dogs/9/care': () => jsonResponse(200, noCare),
      '/api/dogs/9': () => jsonResponse(200, dog({ id: 9, name: 'Luna' })),
      '/api/dogs': () => jsonResponse(201, dog({ id: 9, name: 'Luna' })),
    })

    renderApp('/admin/dogs/new')
    fireEvent.change(await screen.findByLabelText('Name'), { target: { value: ' Luna ' } })
    fireEvent.change(screen.getByLabelText('Year of birth'), { target: { value: '2021' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add dog' }))

    expect(await screen.findByRole('heading', { name: 'Edit Luna' })).toBeInTheDocument()
    expect(bodyOf('/api/dogs', 'POST')).toEqual({
      name: 'Luna',
      breed: null,
      birthYear: 2021,
      description: null,
      status: 'AVAILABLE',
    })
  })

  it('shows what the API says when it refuses', async () => {
    routeFetch(fetchMock, { ...session(), '/api/dogs': () => jsonResponse(400, { detail: 'Name is too long' }) })

    renderApp('/admin/dogs/new')
    fireEvent.change(await screen.findByLabelText('Name'), { target: { value: 'Luna' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add dog' }))

    expect(await screen.findByText('Name is too long')).toBeInTheDocument()
  })
})

describe('editing a dog', () => {
  const careRoute = (value = care({ internalNote: 'Bites strangers' })) => ({
    '/api/dogs/4/care': () => jsonResponse(200, value),
  })

  it('shows not found for an unknown dog', async () => {
    routeFetch(fetchMock, { ...session(), '/api/dogs/4': () => jsonResponse(404, { detail: 'not found' }) })

    renderApp('/admin/dogs/4/edit')

    expect(await screen.findByRole('heading', { name: 'Dog not found' })).toBeInTheDocument()
  })

  it('saves the dog with all its values', async () => {
    routeFetch(fetchMock, { ...session(), ...careRoute(), '/api/dogs/4': () => jsonResponse(200, dog({ id: 4, name: 'Rex' })) })

    renderApp('/admin/dogs/4/edit')
    const name = await screen.findByLabelText('Name')
    expect(name).toHaveValue('Rex')
    fireEvent.change(name, { target: { value: 'Rexy' } })
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'ADOPTED' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save dog' }))

    expect(await screen.findByText('Dog saved.')).toBeInTheDocument()
    expect(bodyOf('/api/dogs/4', 'PUT')).toEqual({
      name: 'Rexy',
      breed: 'Labrador',
      birthYear: 2020,
      description: 'A calm dog who likes long walks.',
      status: 'ADOPTED',
    })
  })

  it('shows the care details and saves a changed set, vaccinations included', async () => {
    routeFetch(fetchMock, { ...session(), ...careRoute(), '/api/dogs/4': () => jsonResponse(200, dog({ id: 4 })) })

    renderApp('/admin/dogs/4/edit')
    expect(await screen.findByLabelText('Microchip number')).toHaveValue('900123')
    expect(screen.getByLabelText(/Internal note/)).toHaveValue('Bites strangers')
    fireEvent.click(screen.getByRole('button', { name: 'Add vaccination' }))
    const vaccines = screen.getAllByLabelText('Vaccine')
    fireEvent.change(vaccines[1], { target: { value: 'Distemper' } })
    fireEvent.change(screen.getAllByLabelText('Given on')[1], { target: { value: '2026-03-01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save care details' }))

    expect(await screen.findByText('Care details saved.')).toBeInTheDocument()
    expect(bodyOf('/api/dogs/4/care', 'PUT')).toMatchObject({
      sterilized: true,
      vaccinations: [
        { name: 'Rabies', vaccinatedOn: '2026-01-05', nextDueOn: '2027-01-05' },
        { name: 'Distemper', vaccinatedOn: '2026-03-01', nextDueOn: null },
      ],
    })
  })

  it('does not send a vaccination without a name or date', async () => {
    routeFetch(fetchMock, { ...session(), ...careRoute(noCare), '/api/dogs/4': () => jsonResponse(200, dog({ id: 4 })) })

    renderApp('/admin/dogs/4/edit')
    fireEvent.click(await screen.findByRole('button', { name: 'Add vaccination' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save care details' }))

    expect(await screen.findByText('Enter the name of the vaccine')).toBeInTheDocument()
    expect(screen.getByText('Enter the date')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).includes('/care') && init?.method === 'PUT')).toBe(false)
  })

  it('removes a vaccination', async () => {
    routeFetch(fetchMock, { ...session(), ...careRoute(), '/api/dogs/4': () => jsonResponse(200, dog({ id: 4 })) })

    renderApp('/admin/dogs/4/edit')
    fireEvent.click(await screen.findByRole('button', { name: 'Remove vaccination 1' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save care details' }))

    await screen.findByText('Care details saved.')
    expect(bodyOf('/api/dogs/4/care', 'PUT')).toMatchObject({ vaccinations: [] })
  })

  it('does not show the care form when the details could not be read, so they cannot be overwritten', async () => {
    routeFetch(fetchMock, {
      ...session(),
      '/api/dogs/4/care': () => jsonResponse(500, { detail: 'Boom' }),
      '/api/dogs/4': () => jsonResponse(200, dog({ id: 4 })),
    })

    renderApp('/admin/dogs/4/edit')

    expect(await screen.findByText('Boom')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save care details' })).not.toBeInTheDocument()
  })

  it('asks before it deletes, and returns to the list afterwards', async () => {
    let deleted = false
    routeFetch(fetchMock, {
      ...session(),
      ...careRoute(),
      '/api/dogs/4': (_url, init) => {
        if (init?.method === 'DELETE') {
          deleted = true
          return new Response(null, { status: 204 })
        }
        return jsonResponse(200, dog({ id: 4, name: 'Rex' }))
      },
      '/api/dogs': () => jsonResponse(200, dogPage([])),
    })

    renderApp('/admin/dogs/4/edit')
    fireEvent.click(await screen.findByRole('button', { name: 'Delete dog' }))
    expect(deleted).toBe(false)
    expect(screen.getByText(/also removes its applications, care details and photos/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(await screen.findByRole('heading', { name: 'Dogs' })).toBeInTheDocument()
    expect(deleted).toBe(true)
  })

  it('keeps the dog when the deletion is cancelled', async () => {
    routeFetch(fetchMock, { ...session(), ...careRoute(), '/api/dogs/4': () => jsonResponse(200, dog({ id: 4 })) })

    renderApp('/admin/dogs/4/edit')
    fireEvent.click(await screen.findByRole('button', { name: 'Delete dog' }))
    fireEvent.click(screen.getByRole('button', { name: 'Keep it' }))

    expect(screen.getByRole('button', { name: 'Delete dog' })).toBeInTheDocument()
  })

  it('shows the reason when the deletion fails', async () => {
    routeFetch(fetchMock, {
      ...session(),
      ...careRoute(),
      '/api/dogs/4': (_url, init) =>
        init?.method === 'DELETE' ? jsonResponse(500, { detail: 'Cannot delete' }) : jsonResponse(200, dog({ id: 4 })),
    })

    renderApp('/admin/dogs/4/edit')
    fireEvent.click(await screen.findByRole('button', { name: 'Delete dog' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(await screen.findByText('Cannot delete')).toBeInTheDocument()
  })
})

describe('care details on the dog page', () => {
  const dogRoute = { '/api/dogs/7': () => jsonResponse(200, dog({ id: 7 })) }

  it('shows an administrator the details, the internal note and the way to edit them', async () => {
    routeFetch(fetchMock, {
      ...session(),
      '/api/dogs/7/care': () => jsonResponse(200, care({ internalNote: 'Bites strangers' })),
      ...dogRoute,
    })

    renderApp('/dogs/7')

    expect(await screen.findByText('Healthy and strong.')).toBeInTheDocument()
    expect(screen.getByText('Rabies')).toBeInTheDocument()
    expect(screen.getByText('5 Jan 2026')).toBeInTheDocument()
    expect(screen.getByText('Bites strangers')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Edit care details' })).toHaveAttribute('href', '/admin/dogs/7/edit')
    expect(screen.getByRole('link', { name: 'Edit dog' })).toHaveAttribute('href', '/admin/dogs/7/edit')
  })

  it('says so when nothing was entered yet', async () => {
    routeFetch(fetchMock, { ...session(), '/api/dogs/7/care': () => jsonResponse(200, noCare), ...dogRoute })

    renderApp('/dogs/7')

    expect(await screen.findByText('No care details have been added yet.')).toBeInTheDocument()
  })

  it('shows the sponsor the details, but not an internal note', async () => {
    routeFetch(fetchMock, {
      ...session(ana),
      '/api/sponsorships/me': () => jsonResponse(200, applicationPage([application({ dogId: 7, status: 'APPROVED' })])),
      '/api/dogs/7/care': () => jsonResponse(200, care()),
      ...dogRoute,
    })

    renderApp('/dogs/7')

    expect(await screen.findByText('Healthy and strong.')).toBeInTheDocument()
    expect(screen.getByText(/You sponsor this dog. Only you and the shelter/)).toBeInTheDocument()
    expect(screen.queryByText(/Internal note/)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit care details' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('status=APPROVED'))).toBe(true)
  })

  it('does not even ask for the details when the user is not the sponsor', async () => {
    routeFetch(fetchMock, {
      ...session(ana),
      '/api/sponsorships/me': () => jsonResponse(200, applicationPage([])),
      '/api/dogs/7/care': () => jsonResponse(200, care()),
      ...dogRoute,
    })

    renderApp('/dogs/7')
    await screen.findByRole('button', { name: 'Apply for sponsorship' })

    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/care'))).toBe(false))
    expect(screen.queryByText('Care details')).not.toBeInTheDocument()
  })

  it('shows a visitor nothing and asks for nothing', async () => {
    routeFetch(fetchMock, dogRoute)

    renderApp('/dogs/7')
    await screen.findByRole('heading', { name: 'Rex' })

    expect(screen.queryByText('Care details')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/care'))).toBe(false)
  })

  it('shows an error and can try again', async () => {
    let fail = true
    routeFetch(fetchMock, {
      ...session(),
      '/api/dogs/7/care': () => (fail ? jsonResponse(500, { detail: 'Boom' }) : jsonResponse(200, care())),
      ...dogRoute,
    })

    renderApp('/dogs/7')
    expect(await screen.findByText('Boom')).toBeInTheDocument()
    fail = false
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Healthy and strong.')).toBeInTheDocument()
  })
})

describe('the dog list', () => {
  it('offers an administrator to add a dog', async () => {
    routeFetch(fetchMock, { ...session(), '/api/dogs': () => jsonResponse(200, dogPage([dog()])) })

    renderApp('/dogs')

    expect(await screen.findByRole('link', { name: 'Add dog' })).toHaveAttribute('href', '/admin/dogs/new')
  })

  it('does not offer it to a user', async () => {
    routeFetch(fetchMock, { ...session(ana), '/api/dogs': () => jsonResponse(200, dogPage([dog()])) })

    renderApp('/dogs')
    await screen.findByText('Rex')

    expect(screen.queryByRole('link', { name: 'Add dog' })).not.toBeInTheDocument()
  })
})

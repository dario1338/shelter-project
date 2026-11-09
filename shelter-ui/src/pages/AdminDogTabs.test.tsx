import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { noCare } from '../test/care'
import { dog } from '../test/dogs'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { renderApp } from '../test/renderApp'
import { admin } from '../test/users'

const fetchMock = setUpFetchMock()

function serve() {
  routeFetch(fetchMock, {
    '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }),
    '/api/auth/me': () => jsonResponse(200, admin),
    '/api/dogs/4/care': () => jsonResponse(200, noCare),
    '/api/dogs/4': () => jsonResponse(200, dog({ id: 4, name: 'Rex' })),
  })
}

const asked = (part: string) => fetchMock.mock.calls.some(([url]) => String(url).includes(part))

describe('tabs on the edit page', () => {
  it('opens the dog first, with the way to delete it, and nothing of the other parts', async () => {
    serve()

    renderApp('/admin/dogs/4/edit')

    expect(await screen.findByLabelText('Name')).toHaveValue('Rex')
    expect(screen.getByRole('tab', { name: 'Dog' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: 'Delete dog' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Add photos')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save care details' })).not.toBeInTheDocument()
  })

  it.each([
    ['photos', 'Photos', () => screen.findByLabelText('Add photos')],
    ['care', 'Care details', () => screen.findByRole('button', { name: 'Save care details' })],
  ])('opens the tab that the address names (%s)', async (tab, label, content) => {
    serve()

    renderApp(`/admin/dogs/4/edit?tab=${tab}`)

    expect(await content()).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: label })).toHaveAttribute('aria-selected', 'true')
  })

  it('opens the dog when the address names a tab that does not exist', async () => {
    serve()

    renderApp('/admin/dogs/4/edit?tab=billing')

    expect(await screen.findByLabelText('Name')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Dog' })).toHaveAttribute('aria-selected', 'true')
  })

  it('moves between the tabs', async () => {
    serve()

    renderApp('/admin/dogs/4/edit')
    fireEvent.click(await screen.findByRole('tab', { name: 'Photos' }))
    expect(await screen.findByLabelText('Add photos')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Photos' })).toHaveAttribute('aria-selected', 'true')
    fireEvent.click(screen.getByRole('tab', { name: 'Care details' }))
    expect(await screen.findByRole('button', { name: 'Save care details' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: 'Dog' }))

    // The panel of the tab that was left is hidden, the one that was opened is shown again.
    await waitFor(() => expect(screen.getByLabelText('Name')).toBeVisible())
    expect(screen.getByRole('tab', { name: 'Dog' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: 'Save care details', hidden: true })).not.toBeVisible()
  })

  it('does not ask for the care details before their tab is opened', async () => {
    serve()

    renderApp('/admin/dogs/4/edit')
    await screen.findByLabelText('Name')
    expect(asked('/care')).toBe(false)
    fireEvent.click(screen.getByRole('tab', { name: 'Care details' }))

    expect(await screen.findByRole('button', { name: 'Save care details' })).toBeInTheDocument()
    expect(asked('/care')).toBe(true)
  })

  it('keeps what was typed when the administrator visits another tab and comes back', async () => {
    serve()

    renderApp('/admin/dogs/4/edit')
    fireEvent.change(await screen.findByLabelText('Name'), { target: { value: 'Rexy' } })
    fireEvent.click(screen.getByRole('tab', { name: 'Photos' }))
    await screen.findByLabelText('Add photos')
    fireEvent.click(screen.getByRole('tab', { name: 'Dog' }))

    expect(await screen.findByLabelText('Name')).toHaveValue('Rexy')
  })
})

describe('tabs on the page for a new dog', () => {
  it('shows the photos and care tabs but does not let them be opened yet', async () => {
    routeFetch(fetchMock, {
      '/api/auth/refresh': () => jsonResponse(200, { accessToken: 't1' }),
      '/api/auth/me': () => jsonResponse(200, admin),
    })

    renderApp('/admin/dogs/new')

    expect(await screen.findByLabelText('Name')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Dog' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Photos' })).toBeDisabled()
    expect(screen.getByRole('tab', { name: 'Care details' })).toBeDisabled()
    expect(screen.getByText(/can be added as soon as the dog is saved/)).toBeInTheDocument()
  })
})

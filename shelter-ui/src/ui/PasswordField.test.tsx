import { ChakraProvider, defaultSystem } from '@chakra-ui/react'
import { fireEvent, render as renderPlain, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { PasswordField } from './PasswordField'
import { renderApp } from '../test/renderApp'
import { setUpFetchMock } from '../test/fetchMock'
import { jsonResponse, routeFetch } from '../test/http'
import { dogPage } from '../test/dogs'

function Harness({ error }: Readonly<{ error?: string }>) {
  const [value, setValue] = useState('')
  return <PasswordField label="Password" value={value} onChange={setValue} error={error} autoComplete="new-password" />
}

// The field needs the theme, as in the application.
function render(ui: React.ReactElement) {
  return renderPlain(<ChakraProvider value={defaultSystem}>{ui}</ChakraProvider>)
}

describe('PasswordField', () => {
  it('hides the password at first and shows it while the button is on', () => {
    render(<Harness />)
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret123' } })

    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
    const show = screen.getByRole('button', { name: 'Show password' })
    expect(show).toHaveAttribute('aria-pressed', 'false')

    fireEvent.click(show)

    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text')
    expect(screen.getByLabelText('Password')).toHaveValue('secret123')
    expect(screen.getByRole('button', { name: 'Hide password' })).toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(screen.getByRole('button', { name: 'Hide password' }))

    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
  })

  it('shows its message and passes the other attributes to the input', () => {
    render(<Harness error="Password is required" />)

    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'new-password')
  })

  it('does not submit the form when the button is used', () => {
    let submitted = false
    render(
      <form
        onSubmit={(event) => {
          event.preventDefault()
          submitted = true
        }}
      >
        <Harness />
      </form>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Show password' }))

    expect(submitted).toBe(false)
  })
})

const fetchMock = setUpFetchMock()

describe('the sign-in and the registration pages', () => {
  it.each(['/login', '/register'])('have the button on %s, and the password is hidden', async (path) => {
    routeFetch(fetchMock, {
      '/api/auth/refresh': () => new Response(null, { status: 401 }),
      '/api/dogs': () => jsonResponse(200, dogPage([])),
    })

    renderApp(path)

    const field = await screen.findByLabelText('Password')
    expect(field).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Show password' })).toBeInTheDocument()
  })
})

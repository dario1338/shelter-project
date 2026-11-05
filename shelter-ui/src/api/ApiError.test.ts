import { describe, expect, it } from 'vitest'
import { ApiError, errorMessage, toApiError } from './ApiError'

describe('toApiError', () => {
  it('uses the detail of a ProblemDetail', async () => {
    const response = new Response(JSON.stringify({ detail: 'Dog not found', title: 'Not Found' }), { status: 404 })

    const error = await toApiError(response)

    expect(error.status).toBe(404)
    expect(error.message).toBe('Dog not found')
  })

  it('falls back to the title, then to a generic message', async () => {
    expect((await toApiError(new Response(JSON.stringify({ title: 'Bad Request' }), { status: 400 }))).message).toBe(
      'Bad Request',
    )
    expect((await toApiError(new Response('<html></html>', { status: 500 }))).message).toBe(
      'Request failed with status 500',
    )
  })
})

describe('errorMessage', () => {
  it('shows the message of an API error', () => {
    expect(errorMessage(new ApiError(409, 'Dog is not available for sponsorship'))).toBe(
      'Dog is not available for sponsorship',
    )
  })

  it.each([502, 503, 504])('explains that the service is starting up on %i', (status) => {
    expect(errorMessage(new ApiError(status, `Request failed with status ${status}`))).toBe(
      'The service is temporarily unavailable. Please try again in a minute.',
    )
  })

  it('does not leak the text of an unexpected error', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toBe(
      'Something went wrong. Please check your connection and try again.',
    )
  })
})

import { describe, expect, it } from 'vitest'
import { ApiError } from './ApiError'
import { createQueryClient, shouldRetry } from './queryClient'

describe('shouldRetry', () => {
  it('does not retry an error of the client, which asking again will not change', () => {
    expect(shouldRetry(0, new ApiError(404, 'Not found'))).toBe(false)
    expect(shouldRetry(0, new ApiError(400, 'Bad request'))).toBe(false)
  })

  it.each([500, 502, 503, 429])('retries a %i, which is usually temporary', (status) => {
    expect(shouldRetry(0, new ApiError(status, 'x'))).toBe(true)
  })

  it('retries a network failure', () => {
    expect(shouldRetry(0, new TypeError('Failed to fetch'))).toBe(true)
  })

  it('gives up after two retries', () => {
    expect(shouldRetry(1, new ApiError(503, 'x'))).toBe(true)
    expect(shouldRetry(2, new ApiError(503, 'x'))).toBe(false)
  })
})

describe('createQueryClient', () => {
  it('does not refetch on focus, which would wake a sleeping server', () => {
    const options = createQueryClient().getDefaultOptions().queries
    expect(options?.refetchOnWindowFocus).toBe(false)
    expect(options?.retry).toBe(shouldRetry)
  })
})

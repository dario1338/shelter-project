import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './ApiError'

const MAX_RETRIES = 2

// A client error (400, 404, ...) will not change by asking again. Network failures, 5xx and the 429
// that a sleeping free instance answers with are worth another try.
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES) {
    return false
  }
  if (error instanceof ApiError) {
    return error.status >= 500 || error.status === 429
  }
  return true
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: shouldRetry,
        // The data changes rarely, and a refetch on every focus would wake a sleeping server.
        staleTime: 60_000,
        refetchOnWindowFocus: false,
      },
    },
  })
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

// Builds an ApiError from a failed response, using the ProblemDetail "detail"/"title" when present.
export async function toApiError(response: Response): Promise<ApiError> {
  let message = `Request failed with status ${response.status}`
  try {
    const problem = (await response.json()) as { detail?: string; title?: string }
    message = problem.detail ?? problem.title ?? message
  } catch {
    // The body is empty or not JSON; keep the generic message.
  }
  return new ApiError(response.status, message)
}

// 502-504 come from the proxy or the hosting platform while an instance is starting up
// (free instances sleep when idle), so the body is an HTML page and not a ProblemDetail.
const UNAVAILABLE_STATUSES = new Set([502, 503, 504])

// The text shown to the user for any failed call.
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return UNAVAILABLE_STATUSES.has(error.status)
      ? 'The service is temporarily unavailable. Please try again in a minute.'
      : error.message
  }
  return 'Something went wrong. Please check your connection and try again.'
}

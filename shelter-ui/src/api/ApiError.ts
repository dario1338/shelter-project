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
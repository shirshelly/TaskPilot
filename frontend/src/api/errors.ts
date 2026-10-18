/** Codes sent by the backend in Problem Details bodies (§33), plus two client-side ones. */
export type ApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'TASK_NOT_FOUND'
  | 'INVALID_STATE_TRANSITION'
  | 'VERSION_CONFLICT'
  | 'REQUEST_NOT_SUPPORTED'
  | 'INTERNAL_ERROR'
  /** The request never got an HTTP response (server down, offline). */
  | 'NETWORK_ERROR'
  /** A response we can't classify. */
  | 'UNKNOWN'

const SERVER_CODES: ReadonlySet<string> = new Set([
  'VALIDATION_FAILED',
  'TASK_NOT_FOUND',
  'INVALID_STATE_TRANSITION',
  'VERSION_CONFLICT',
  'REQUEST_NOT_SUPPORTED',
  'INTERNAL_ERROR',
])

export class ApiError extends Error {
  readonly status: number
  readonly code: ApiErrorCode
  /** The server's `detail`, if any. Safe to show, but UI copy is chosen by `code`. */
  readonly detail: string | null

  constructor(status: number, code: ApiErrorCode, detail: string | null) {
    super(detail ?? code)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.detail = detail
  }
}

export function networkError(): ApiError {
  return new ApiError(0, 'NETWORK_ERROR', null)
}

/**
 * Turns a non-2xx response into an ApiError. Prefers the backend's `code`; falls back to the
 * status for bodies that aren't ours (e.g. the dev proxy answering while Spring is down).
 */
export async function errorFromResponse(response: Response): Promise<ApiError> {
  let code: string | undefined
  let detail: string | null = null
  if ((response.headers.get('Content-Type') ?? '').includes('json')) {
    try {
      const body: unknown = await response.json()
      if (body && typeof body === 'object') {
        const problem = body as { code?: unknown; detail?: unknown }
        if (typeof problem.code === 'string') code = problem.code
        if (typeof problem.detail === 'string') detail = problem.detail
      }
    } catch {
      // Malformed body: classify by status below.
    }
  }
  const resolved = code !== undefined && SERVER_CODES.has(code) ? (code as ApiErrorCode) : codeForStatus(response.status)
  return new ApiError(response.status, resolved, detail)
}

function codeForStatus(status: number): ApiErrorCode {
  if (status === 400) return 'VALIDATION_FAILED'
  if (status === 404) return 'TASK_NOT_FOUND'
  if (status === 412) return 'VERSION_CONFLICT'
  if (status === 409) return 'INVALID_STATE_TRANSITION'
  if (status === 502 || status === 503 || status === 504) return 'NETWORK_ERROR'
  if (status >= 500) return 'INTERNAL_ERROR'
  return 'UNKNOWN'
}

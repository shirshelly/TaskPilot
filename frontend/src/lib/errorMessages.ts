import { ApiError } from '@/api/errors'

/**
 * User-facing copy for a failed action, chosen by the backend's error code — never by parsing
 * messages. Only validation errors show the server's `detail`, which describes the input.
 */
export function describeActionError(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Something went wrong. Try again.'
  switch (error.code) {
    case 'NETWORK_ERROR':
      return "Can't reach the server. Try again."
    case 'TASK_NOT_FOUND':
      return 'This task no longer exists.'
    case 'INVALID_STATE_TRANSITION':
    case 'VERSION_CONFLICT':
      return 'This task changed elsewhere, so the list has been refreshed.'
    case 'VALIDATION_FAILED':
      return error.detail ? `Not accepted: ${error.detail}` : "Some values weren't accepted."
    default:
      return 'Something went wrong. Try again.'
  }
}

export function isApiErrorCode(error: unknown, code: ApiError['code']): boolean {
  return error instanceof ApiError && error.code === code
}

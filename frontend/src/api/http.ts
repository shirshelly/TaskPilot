import { errorFromResponse, networkError } from './errors'

/** Browser-side prefix; the Vite dev proxy strips it before forwarding to Spring Boot. */
const API_BASE = '/api'

type QueryParams = Record<string, string | number | null | undefined>

interface RequestOptions {
  params?: QueryParams
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
}

/**
 * The one place that talks HTTP. Non-2xx responses become ApiError (classified by the backend's
 * `code`); a 204 resolves to undefined.
 */
export async function apiRequest<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const { params, body, headers, signal } = options
  let response: Response
  try {
    response = await fetch(API_BASE + path + toQueryString(params), {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined && { 'Content-Type': 'application/json' }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    // Let TanStack Query see its own cancellations; anything else means no HTTP response at all.
    if (signal?.aborted) throw error
    throw networkError()
  }
  if (!response.ok) throw await errorFromResponse(response)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export function apiGet<T>(path: string, params?: QueryParams, signal?: AbortSignal): Promise<T> {
  return apiRequest<T>('GET', path, { params, signal })
}

function toQueryString(params?: QueryParams): string {
  if (!params) return ''
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined) search.set(key, String(value))
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}

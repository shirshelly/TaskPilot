import { errorFromResponse, networkError } from './errors'

/** Browser-side prefix; the Vite dev proxy strips it before forwarding to Spring Boot. */
const API_BASE = '/api'

type QueryParams = Record<string, string | number | null | undefined>

export async function apiGet<T>(path: string, params?: QueryParams, signal?: AbortSignal): Promise<T> {
  let response: Response
  try {
    response = await fetch(API_BASE + path + toQueryString(params), {
      headers: { Accept: 'application/json' },
      signal,
    })
  } catch (error) {
    // Let TanStack Query see its own cancellations; anything else means no HTTP response at all.
    if (signal?.aborted) throw error
    throw networkError()
  }
  if (!response.ok) throw await errorFromResponse(response)
  return (await response.json()) as T
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

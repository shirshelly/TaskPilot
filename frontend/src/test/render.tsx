import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { vi } from 'vitest'
import type { Task } from '@/api/types'
import { AppTimeProvider } from '@/app/appTime'
import { TaskOverlaysProvider } from '@/app/taskOverlays'
import { Toaster } from 'sonner'

export const TEST_ZONE = 'Asia/Jerusalem'

/**
 * Renders `ui` at `route` the way AppShell does: fresh query client (no retries), app-time
 * context, the task dialogs, and a toaster.
 */
export function renderWithApp(ui: ReactElement, { route = '/', zone = TEST_ZONE } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const element = (
    <AppTimeProvider zone={zone}>
      <TaskOverlaysProvider>{ui}</TaskOverlaysProvider>
    </AppTimeProvider>
  )
  const router = createMemoryRouter([{ path: '*', element }], { initialEntries: [route] })
  const result = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster />
    </QueryClientProvider>,
  )
  return { ...result, router, queryClient }
}

export const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

/** Replaces global fetch; the handler sees the request URL (path relative to /api) and init. */
export function stubFetch(handler: (url: URL, init?: RequestInit) => Response | Promise<Response>) {
  const mock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) =>
    handler(new URL(String(input), 'http://localhost'), init),
  )
  vi.stubGlobal('fetch', mock)
  return mock
}

/** Paths and query strings of every request made so far, e.g. "/api/recommendations?availableMinutes=30". */
export function requestedUrls(mock: ReturnType<typeof stubFetch>): string[] {
  return mock.mock.calls.map(([input]) => {
    const url = new URL(String(input), 'http://localhost')
    return url.pathname + url.search
  })
}

let sequence = 0
export function task(overrides: Partial<Task> = {}): Task {
  sequence += 1
  return {
    id: `00000000-0000-0000-0000-${String(sequence).padStart(12, '0')}`,
    title: `Task ${sequence}`,
    description: null,
    status: 'ACTIVE',
    importance: 3,
    estimatedDurationMinutes: null,
    minSessionMinutes: null,
    deadline: null,
    scheduledFor: null,
    createdAt: '2026-10-01T09:00:00Z',
    updatedAt: '2026-10-01T09:00:00Z',
    completedAt: null,
    isOverdue: false,
    version: 0,
    ...overrides,
  }
}

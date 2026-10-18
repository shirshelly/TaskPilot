import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { json, renderWithApp, requestedUrls, stubFetch, task } from '@/test/render'
import { TasksPage } from './TasksPage'

afterEach(() => vi.unstubAllGlobals())

describe('TasksPage', () => {
  it('defaults to Active and switches filters through the backend status filter', async () => {
    const fetchMock = stubFetch((url) =>
      json(
        url.searchParams.get('status') === 'COMPLETED'
          ? [task({ title: 'Filed taxes', status: 'COMPLETED', completedAt: '2026-10-01T10:00:00Z' })]
          : [task({ title: 'Water plants' })],
      ),
    )
    const { router } = renderWithApp(<TasksPage />, { route: '/tasks' })

    expect(await screen.findByText('Water plants')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Active' })).toBeChecked()

    await userEvent.click(screen.getByRole('radio', { name: 'Completed' }))

    expect(await screen.findByText('Filed taxes')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?status=completed')
    expect(requestedUrls(fetchMock)).toEqual(['/api/tasks?status=ACTIVE', '/api/tasks?status=COMPLETED'])
  })

  it('All sends no status filter', async () => {
    const fetchMock = stubFetch(() => json([]))
    renderWithApp(<TasksPage />, { route: '/tasks?status=all' })

    expect(await screen.findByText('No tasks yet.')).toBeInTheDocument()
    expect(requestedUrls(fetchMock)).toEqual(['/api/tasks'])
  })

  it('shows a retryable error when the server cannot be reached', async () => {
    let up = false
    stubFetch(() => {
      if (!up) throw new TypeError('Failed to fetch')
      return json([task({ title: 'Back online' })])
    })
    renderWithApp(<TasksPage />, { route: '/tasks' })

    expect(await screen.findByRole('alert')).toHaveTextContent("Can't reach the TaskPilot server")
    up = true
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(screen.getByText('Back online')).toBeInTheDocument())
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TasksPage } from '@/pages/TasksPage'
import { json, renderWithApp, requestedUrls, stubFetch, task } from '@/test/render'

afterEach(() => vi.unstubAllGlobals())

const problem = (status: number, code: string) =>
  new Response(JSON.stringify({ status, code }), { status, headers: { 'Content-Type': 'application/problem+json' } })

describe('task row actions', () => {
  it('completes a task only once the server confirms, showing a pending state meanwhile', async () => {
    const plants = task({ title: 'Water plants' })
    let completed = false
    let finish: () => void = () => {}
    const fetchMock = stubFetch((_url, init) => {
      if (init?.method === 'POST') {
        return new Promise<Response>((resolve) => {
          finish = () => {
            completed = true
            resolve(json({ ...plants, status: 'COMPLETED', completedAt: '2026-10-21T09:00:00Z', version: 1 }))
          }
        })
      }
      return json(completed ? [] : [plants])
    })
    renderWithApp(<TasksPage />, { route: '/tasks' })

    const checkbox = await screen.findByRole('checkbox', { name: 'Complete “Water plants”' })
    await userEvent.click(checkbox)

    // Still on screen and locked while the request is in flight; a second click does nothing.
    expect(checkbox).toBeDisabled()
    expect(checkbox.closest('li')).toHaveAttribute('aria-busy', 'true')
    await userEvent.click(checkbox)

    finish()
    expect(await screen.findByText('Completed “Water plants”')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('checkbox', { name: 'Complete “Water plants”' })).not.toBeInTheDocument())
    expect(requestedUrls(fetchMock).filter((u) => u.endsWith('/complete'))).toHaveLength(1)
  })

  it('explains a 409 from a stale view and refreshes the list', async () => {
    const report = task({ title: 'File report' })
    let calls = 0
    const fetchMock = stubFetch((_url, init) => {
      if (init?.method === 'POST') return problem(409, 'INVALID_STATE_TRANSITION')
      calls += 1
      return json(calls === 1 ? [report] : [])
    })
    renderWithApp(<TasksPage />, { route: '/tasks' })

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete “File report”' }))

    expect(await screen.findByText('This task changed elsewhere, so the list has been refreshed.')).toBeInTheDocument()
    await waitFor(() => expect(requestedUrls(fetchMock).filter((u) => u === '/api/tasks?status=ACTIVE')).toHaveLength(2))
  })

  it('deletes after confirmation, treating "already gone" as done', async () => {
    const old = task({ title: 'Old idea' })
    const fetchMock = stubFetch((_url, init) => (init?.method === 'DELETE' ? problem(404, 'TASK_NOT_FOUND') : json([old])))
    renderWithApp(<TasksPage />, { route: '/tasks' })

    await userEvent.click(await screen.findByRole('button', { name: 'Actions for “Old idea”' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete…' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('Delete “Old idea”?')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText('That task was already deleted.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'DELETE')).toHaveLength(1)
  })

  it('returns keyboard focus to the row after the editor closes', async () => {
    stubFetch((url) => (url.pathname.startsWith('/api/tasks/') ? json(task({ title: 'Plan trip' })) : json([task({ title: 'Plan trip' })])))
    renderWithApp(<TasksPage />, { route: '/tasks' })

    const row = await screen.findByRole('button', { name: /^Plan trip/ })
    row.focus()
    await userEvent.keyboard('{Enter}')
    await screen.findByRole('dialog', { name: 'Edit task' })
    await userEvent.keyboard('{Escape}')

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(screen.getByRole('button', { name: /^Plan trip/ })).toHaveFocus())
  })

  it('offers Reopen (not Schedule) for a completed task', async () => {
    // Completed "now", so it lands in the expanded Today group.
    const done = task({ title: 'Taxes', status: 'COMPLETED', completedAt: new Date().toISOString() })
    stubFetch(() => json([done]))
    renderWithApp(<TasksPage />, { route: '/tasks?status=completed' })

    expect(await screen.findByRole('button', { name: 'Reopen' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: '“Taxes” is completed' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Actions for “Taxes”' }))
    expect(screen.queryByRole('menuitem', { name: /schedule/i })).not.toBeInTheDocument()
  })
})

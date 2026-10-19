import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Task } from '@/api/types'
import { TasksPage } from '@/pages/TasksPage'
import { json, renderWithApp, requestedUrls, stubFetch, task } from '@/test/render'
import { FocusPanel } from './FocusPanel'

afterEach(() => vi.unstubAllGlobals())

const completions = (mock: ReturnType<typeof stubFetch>) => requestedUrls(mock).filter((url) => url.endsWith('/complete'))

/** Lists return `tasks`; POST /complete waits until `finish()` is called. */
function server(tasks: Task[]) {
  let finish: () => void = () => {}
  let done = false
  const mock = stubFetch((url, init) => {
    if (init?.method === 'POST') {
      return new Promise<Response>((resolve) => {
        finish = () => {
          done = true
          const id = url.pathname.split('/')[3]
          const completed = tasks.find((t) => t.id === id)!
          resolve(json({ ...completed, status: 'COMPLETED', completedAt: '2026-10-21T09:00:00Z', version: 1 }))
        }
      })
    }
    return json(done ? [] : tasks)
  })
  return { mock, finish: () => finish() }
}

const book = task({ title: 'Read a book', estimatedDurationMinutes: 600, minSessionMinutes: 10 })
const call = task({ title: 'Call dentist', estimatedDurationMinutes: 10 })

describe('session-aware completion', () => {
  it('completes a task without a minimum session in one click', async () => {
    const { mock, finish } = server([call])
    renderWithApp(<TasksPage />, { route: '/tasks' })

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete “Call dentist”' }))
    finish()

    expect(await screen.findByText('Completed “Call dentist”')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(completions(mock)).toHaveLength(1)
  })

  it('"I’m done for now" keeps the task active and sends nothing', async () => {
    const { mock } = server([book])
    renderWithApp(<TasksPage />, { route: '/tasks' })

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete “Read a book”' }))
    const dialog = await screen.findByRole('dialog', { name: 'What did you finish?' })
    await userEvent.click(within(dialog).getByRole('button', { name: /I’m done for now/ }))

    expect(await screen.findByText('Task stays active. Pick it up whenever you’re ready.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(completions(mock)).toEqual([])
    expect(screen.getByRole('checkbox', { name: 'Complete “Read a book”' })).toBeEnabled()
  })

  it('"I finished the entire task" completes once, locked until the server confirms', async () => {
    const { mock, finish } = server([book])
    renderWithApp(<TasksPage />, { route: '/tasks' })

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete “Read a book”' }))
    const dialog = await screen.findByRole('dialog', { name: 'What did you finish?' })
    const finished = within(dialog).getByRole('button', { name: /I finished the entire task/ })
    await userEvent.click(finished)

    expect(finished).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: /I’m done for now/ })).toBeDisabled()
    await userEvent.click(finished)
    finish()

    expect(await screen.findByText('Completed “Read a book”')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(completions(mock)).toEqual([`/api/tasks/${book.id}/complete`])
  })

  it('the featured recommendation asks the same question', async () => {
    const { mock } = server([book, call])
    renderWithApp(<FocusPanel />)

    await userEvent.click(await screen.findByRole('button', { name: 'Mark done' }))

    expect(await screen.findByRole('dialog', { name: 'What did you finish?' })).toHaveTextContent('Read a book')
    expect(completions(mock)).toEqual([])
  })

  it('alternatives under the featured card use the same flow', async () => {
    const { mock } = server([call, book])
    renderWithApp(<FocusPanel />)

    const alternatives = await screen.findByRole('list', { name: 'Other suggestions' })
    await userEvent.click(within(alternatives).getByRole('checkbox', { name: 'Complete “Read a book”' }))

    expect(await screen.findByRole('dialog', { name: 'What did you finish?' })).toBeInTheDocument()
    expect(completions(mock)).toEqual([])
  })
})

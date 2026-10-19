import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Task } from '@/api/types'
import { json, renderWithApp, stubFetch, task } from '@/test/render'
import { EditTaskSheet } from './EditTaskSheet'

afterEach(() => vi.unstubAllGlobals())

const problem = (status: number, code: string) =>
  new Response(JSON.stringify({ status, code }), { status, headers: { 'Content-Type': 'application/problem+json' } })

const patchCalls = (mock: ReturnType<typeof stubFetch>) =>
  mock.mock.calls
    .filter(([, init]) => init?.method === 'PATCH')
    .map(([, init]) => ({
      body: JSON.parse(String(init!.body)),
      ifMatch: (init!.headers as Record<string, string>)['If-Match'],
    }))

/**
 * A tiny in-memory server for one task: GET returns it, PATCH applies the body if If-Match
 * matches its version (else 412 VERSION_CONFLICT). `server.current` can be changed by a test
 * to simulate another client.
 */
function fakeServer(initial: Task) {
  const server = { current: initial }
  const mock = stubFetch((url, init) => {
    if (url.pathname === `/api/tasks/${initial.id}` && init?.method === 'PATCH') {
      const ifMatch = (init.headers as Record<string, string>)['If-Match']
      if (ifMatch !== `"${server.current.version}"`) return problem(412, 'VERSION_CONFLICT')
      server.current = { ...server.current, ...JSON.parse(String(init.body)), version: server.current.version + 1 }
      return json(server.current)
    }
    if (url.pathname === `/api/tasks/${initial.id}`) return json(server.current)
    return json([])
  })
  return { server, mock }
}

const base = task({
  title: 'Write report',
  description: 'Q4 numbers',
  importance: 3,
  estimatedDurationMinutes: 45,
  deadline: '2026-10-23',
  version: 3,
})

function openSheet(row: Task = base) {
  const onOpenChange = vi.fn()
  const view = renderWithApp(<EditTaskSheet task={row} open onOpenChange={onOpenChange} />)
  return { ...view, onOpenChange }
}

describe('EditTaskSheet', () => {
  it('sends only the changed fields, an explicit null for a cleared one, and If-Match', async () => {
    const { mock } = fakeServer(base)
    const { onOpenChange } = openSheet()
    const save = screen.getByRole('button', { name: 'Save' })
    expect(save).toBeDisabled()

    const title = screen.getByLabelText('Title')
    await userEvent.clear(title)
    await userEvent.type(title, 'Write the report')
    await userEvent.click(screen.getByRole('button', { name: 'Clear due date' }))
    await userEvent.click(save)

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
    expect(patchCalls(mock)).toEqual([{ body: { title: 'Write the report', deadline: null }, ifMatch: '"3"' }])
  })

  it('clears the minimum session with an explicit null and leaves the total alone', async () => {
    const ongoing = { ...base, estimatedDurationMinutes: 600, minSessionMinutes: 10 }
    const { mock } = fakeServer(ongoing)
    const { onOpenChange } = openSheet(ongoing)

    await userEvent.clear(screen.getByLabelText('Minimum session'))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
    expect(patchCalls(mock)).toEqual([{ body: { minSessionMinutes: null }, ifMatch: '"3"' }])
  })

  it('on a version conflict keeps the draft, explains the differences, and saves only my fields over the latest after confirming the overlap', async () => {
    const { server, mock } = fakeServer(base)
    const { onOpenChange } = openSheet()
    await waitFor(() => expect(mock).toHaveBeenCalled())

    // Another client changes description and importance while I edit title and importance.
    server.current = { ...base, description: 'Q4 numbers + charts', importance: 5, version: 4 }
    const title = screen.getByLabelText('Title')
    await userEvent.clear(title)
    await userEvent.type(title, 'Write the report')
    await userEvent.click(screen.getByRole('radio', { name: '4 – High' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('This task changed since you started editing.')).toBeInTheDocument()
    expect(screen.getByLabelText('Title')).toHaveValue('Write the report')
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(onOpenChange).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Review changes' }))
    const review = screen.getByRole('alert')
    expect(within(review).getByText('Changed on both sides')).toBeInTheDocument()
    expect(within(review).getByText('Changed elsewhere · kept')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Save mine over latest' }))
    const confirm = await screen.findByRole('alertdialog')
    expect(confirm).toHaveTextContent('Importance was also changed elsewhere')
    await userEvent.click(within(confirm).getByRole('button', { name: 'Replace with mine' }))

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
    expect(patchCalls(mock)).toEqual([
      { body: { title: 'Write the report', importance: 4 }, ifMatch: '"3"' },
      // Rebased on version 4: only my fields, so the server's description survives.
      { body: { title: 'Write the report', importance: 4 }, ifMatch: '"4"' },
    ])
    expect(server.current).toMatchObject({ title: 'Write the report', importance: 4, description: 'Q4 numbers + charts' })
  })

  it('"Discard my changes" loads the latest version into the form without saving', async () => {
    const { server, mock } = fakeServer(base)
    openSheet()
    await waitFor(() => expect(mock).toHaveBeenCalled())
    server.current = { ...base, title: 'Renamed elsewhere', version: 4 }

    await userEvent.type(screen.getByLabelText('Description'), ' and charts')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Review changes' }))
    await userEvent.click(screen.getByRole('button', { name: 'Discard my changes' }))

    expect(screen.getByLabelText('Title')).toHaveValue('Renamed elsewhere')
    expect(screen.getByLabelText('Description')).toHaveValue('Q4 numbers')
    expect(screen.queryByText('This task changed since you started editing.')).not.toBeInTheDocument()
    expect(patchCalls(mock)).toHaveLength(1)
  })

  it('quietly follows a newer server version when nothing has been typed yet', async () => {
    fakeServer({ ...base, title: 'Fresher title', version: 4 })
    openSheet(base)

    await waitFor(() => expect(screen.getByLabelText('Title')).toHaveValue('Fresher title'))
    expect(screen.queryByText('This task changed since you started editing.')).not.toBeInTheDocument()
  })

  it('asks before discarding unsaved edits on close', async () => {
    fakeServer(base)
    const { onOpenChange } = openSheet()
    await userEvent.type(screen.getByLabelText('Title'), '!')

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    const confirm = await screen.findByRole('alertdialog')
    await userEvent.click(within(confirm).getByRole('button', { name: 'Keep editing' }))
    expect(onOpenChange).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Discard' }))
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('shows when the task was deleted elsewhere and offers no save', async () => {
    stubFetch(() => problem(404, 'TASK_NOT_FOUND'))
    openSheet()

    expect(await screen.findByText('This task was deleted elsewhere.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    // Footer "Close" (plus the sheet's own X, also labelled Close).
    for (const close of screen.getAllByRole('button', { name: 'Close' })) expect(close).toBeEnabled()
  })
})

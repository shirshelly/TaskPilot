import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Task } from '@/api/types'
import { json, renderWithApp, stubFetch, task } from '@/test/render'
import { QuickCreateDialog } from './QuickCreateDialog'
import { ScheduleDialog } from './ScheduleDialog'

// Wednesday 21 Oct 2026, 12:00 in Jerusalem. Only Date is faked, so timers and promises run normally.
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-21T09:00:00Z'))
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const bodies = (mock: ReturnType<typeof stubFetch>, method: string) =>
  mock.mock.calls.filter(([, init]) => init?.method === method).map(([, init]) => JSON.parse(String(init!.body)))

describe('QuickCreateDialog', () => {
  it('creates from just a title, and a double submit sends one request', async () => {
    let resolve: (task: Task) => void = () => {}
    const fetchMock = stubFetch((_url, init) =>
      init?.method === 'POST' ? new Promise((r) => (resolve = (t) => r(json(t, 201)))) : json([]),
    )
    const onOpenChange = vi.fn()
    renderWithApp(<QuickCreateDialog open onOpenChange={onOpenChange} />)

    await userEvent.type(screen.getByLabelText('Title'), 'Buy milk{Enter}')
    expect(screen.getByRole('button', { name: 'Add task' })).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Title'), '{Enter}')
    resolve(task({ title: 'Buy milk' }))

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
    expect(bodies(fetchMock, 'POST')).toEqual([{ title: 'Buy milk', importance: 3 }])
    expect(await screen.findByText('Added “Buy milk”')).toBeInTheDocument()
  })

  it('sends More options in API form: Start on as the start of that day in the app zone', async () => {
    const fetchMock = stubFetch((_url, init) =>
      init?.method === 'POST'
        ? json(task({ title: 'Dentist', status: 'SCHEDULED', scheduledFor: '2026-10-25T21:00:00Z' }), 201)
        : json([]),
    )
    renderWithApp(<QuickCreateDialog open onOpenChange={() => {}} />)

    await userEvent.type(screen.getByLabelText('Title'), 'Dentist')
    await userEvent.click(screen.getByRole('button', { name: 'More options' }))
    await userEvent.type(screen.getByLabelText('Estimated duration'), '45')
    fireEvent.change(screen.getByLabelText(/Start on/), { target: { value: '2026-10-26' } })
    fireEvent.change(screen.getByLabelText(/Due by/), { target: { value: '2026-10-30' } })
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }))

    await waitFor(() =>
      expect(bodies(fetchMock, 'POST')).toEqual([
        {
          title: 'Dentist',
          importance: 3,
          estimatedDurationMinutes: 45,
          deadline: '2026-10-30',
          // 26 Oct is after Israel's DST change, so the offset is +02:00.
          scheduledFor: '2026-10-26T00:00:00.000+02:00',
        },
      ]),
    )
    expect(await screen.findByText('Scheduled “Dentist”')).toBeInTheDocument()
  })

  it('sends a minimum session only when one is entered', async () => {
    const fetchMock = stubFetch((_url, init) =>
      init?.method === 'POST' ? json(task({ title: 'Read a book', estimatedDurationMinutes: 600, minSessionMinutes: 10 }), 201) : json([]),
    )
    renderWithApp(<QuickCreateDialog open onOpenChange={() => {}} />)

    await userEvent.type(screen.getByLabelText('Title'), 'Read a book')
    await userEvent.click(screen.getByRole('button', { name: 'More options' }))
    await userEvent.type(screen.getByLabelText('Estimated duration'), '600')
    await userEvent.type(screen.getByLabelText('Minimum session'), '10')
    expect(screen.getByText('Shortest useful amount of time you can spend on this task.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }))

    await waitFor(() =>
      expect(bodies(fetchMock, 'POST')).toEqual([
        { title: 'Read a book', importance: 3, estimatedDurationMinutes: 600, minSessionMinutes: 10 },
      ]),
    )
  })

  it('rejects a minimum session that is not a positive whole number', async () => {
    const fetchMock = stubFetch(() => json([]))
    renderWithApp(<QuickCreateDialog open onOpenChange={() => {}} />)

    await userEvent.type(screen.getByLabelText('Title'), 'Read a book')
    await userEvent.click(screen.getByRole('button', { name: 'More options' }))
    await userEvent.type(screen.getByLabelText('Minimum session'), '0')
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }))

    expect(screen.getByText('Use at least 1 minute.')).toBeInTheDocument()
    expect(bodies(fetchMock, 'POST')).toEqual([])
  })

  it('accepts hours, sends whole minutes, and warns (without blocking) when the session is longer than the total', async () => {
    const fetchMock = stubFetch((_url, init) => (init?.method === 'POST' ? json(task({ title: 'Thesis' }), 201) : json([])))
    renderWithApp(<QuickCreateDialog open onOpenChange={() => {}} />)

    await userEvent.type(screen.getByLabelText('Title'), 'Thesis')
    await userEvent.click(screen.getByRole('button', { name: 'More options' }))
    const total = screen.getByRole('radiogroup', { name: 'Estimated duration unit' })
    await userEvent.click(within(total).getByRole('radio', { name: 'Hours' }))
    await userEvent.type(screen.getByLabelText('Estimated duration'), '1.5')
    await userEvent.type(screen.getByLabelText('Minimum session'), '120')

    const warning = 'Minimum session is longer than the estimated total duration. Is that intentional?'
    expect(screen.getByText(warning)).toBeInTheDocument()
    await userEvent.clear(screen.getByLabelText('Minimum session'))
    await userEvent.type(screen.getByLabelText('Minimum session'), '30')
    expect(screen.queryByText(warning)).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Minimum session'), '0') // 300 min: warning back, still savable
    expect(screen.getByText(warning)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }))

    await waitFor(() =>
      expect(bodies(fetchMock, 'POST')).toEqual([
        { title: 'Thesis', importance: 3, estimatedDurationMinutes: 90, minSessionMinutes: 300 },
      ]),
    )
  })

  it('rejects hours that are not a whole number of minutes instead of rounding', async () => {
    const fetchMock = stubFetch(() => json([]))
    renderWithApp(<QuickCreateDialog open onOpenChange={() => {}} />)

    await userEvent.type(screen.getByLabelText('Title'), 'Odd')
    await userEvent.click(screen.getByRole('button', { name: 'More options' }))
    await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Estimated duration unit' })).getByRole('radio', { name: 'Hours' }))
    await userEvent.type(screen.getByLabelText('Estimated duration'), '1.33')
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }))

    expect(screen.getByText(/isn't a whole number of minutes/)).toBeInTheDocument()
    expect(bodies(fetchMock, 'POST')).toEqual([])
  })

  it('requires a title before sending anything', async () => {
    const fetchMock = stubFetch(() => json([]))
    renderWithApp(<QuickCreateDialog open onOpenChange={() => {}} />)

    await userEvent.click(screen.getByRole('button', { name: 'Add task' }))

    expect(screen.getByText('Give the task a title.')).toBeInTheDocument()
    expect(bodies(fetchMock, 'POST')).toEqual([])
  })
})

describe('ScheduleDialog', () => {
  const report = task({ title: 'Write report', deadline: '2026-10-22' })

  it('schedules for tomorrow by default, as the start of that day', async () => {
    const fetchMock = stubFetch((_url, init) =>
      init?.method === 'POST' ? json({ ...report, status: 'SCHEDULED', scheduledFor: '2026-10-21T21:00:00Z' }) : json([]),
    )
    const onOpenChange = vi.fn()
    renderWithApp(<ScheduleDialog task={report} open onOpenChange={onOpenChange} />)

    await userEvent.click(screen.getByRole('button', { name: 'Schedule' }))

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
    expect(bodies(fetchMock, 'POST')).toEqual([{ scheduledFor: '2026-10-22T00:00:00.000+03:00' }])
    expect(await screen.findByText('Scheduled for tomorrow')).toBeInTheDocument()
  })

  it('reports the outcome from the response: scheduling for today keeps it active', async () => {
    const fetchMock = stubFetch((_url, init) =>
      init?.method === 'POST' ? json({ ...report, scheduledFor: '2026-10-21T13:30:00Z' }) : json([]),
    )
    renderWithApp(<ScheduleDialog task={report} open onOpenChange={() => {}} />)

    await userEvent.click(screen.getByRole('radio', { name: 'Today' }))
    fireEvent.change(screen.getByLabelText(/Time/), { target: { value: '16:30' } })
    await userEvent.click(screen.getByRole('button', { name: 'Schedule' }))

    expect(await screen.findByText('Scheduled for today — it stays active')).toBeInTheDocument()
    expect(bodies(fetchMock, 'POST')).toEqual([{ scheduledFor: '2026-10-21T16:30:00.000+03:00' }])
  })

  it('warns, without blocking, when the start is after the due date', async () => {
    stubFetch(() => json([]))
    renderWithApp(<ScheduleDialog task={report} open onOpenChange={() => {}} />)

    await userEvent.click(screen.getByRole('radio', { name: 'Pick a date' }))
    fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2026-10-28' } })

    expect(screen.getByText('Starts after its due date.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Schedule' })).toBeEnabled()
  })

  it('shows a validation error from the server inline and keeps the dialog open', async () => {
    stubFetch((_url, init) =>
      init?.method === 'POST'
        ? new Response(JSON.stringify({ status: 400, code: 'VALIDATION_FAILED', detail: 'scheduledFor must not be in the past' }), {
            status: 400,
            headers: { 'Content-Type': 'application/problem+json' },
          })
        : json([]),
    )
    const onOpenChange = vi.fn()
    renderWithApp(<ScheduleDialog task={report} open onOpenChange={onOpenChange} />)

    await userEvent.click(screen.getByRole('button', { name: 'Schedule' }))

    expect(await screen.findByText('Not accepted: scheduledFor must not be in the past')).toBeInTheDocument()
    expect(onOpenChange).not.toHaveBeenCalled()
  })
})

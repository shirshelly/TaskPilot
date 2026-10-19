import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TasksPage } from '@/pages/TasksPage'
import { json, renderWithApp, stubFetch, task } from '@/test/render'

// Wednesday 21 Oct 2026, 12:00 in Jerusalem. Only Date is faked.
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-21T09:00:00Z'))
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const done = (title: string, completedAt: string) => task({ title, status: 'COMPLETED', completedAt })

describe('Completed tab', () => {
  it('shows date groups with counts; recent ones open, older ones collapsed and expandable', async () => {
    stubFetch(() =>
      json([
        done('Filed taxes', '2026-10-21T07:00:00Z'),
        done('Booked flights', '2026-10-21T06:00:00Z'),
        done('Watered plants', '2026-10-20T08:00:00Z'),
        done('Renewed passport', '2026-10-19T08:00:00Z'),
        done('Fixed the bike', '2026-09-12T08:00:00Z'),
      ]),
    )
    renderWithApp(<TasksPage />, { route: '/tasks?status=completed' })

    // Wait with a cheap text query; role queries over many rows are slow in jsdom.
    await screen.findByText('Filed taxes')
    const today = screen.getByRole('button', { name: /Today 2 tasks/ })
    expect(today).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('button', { name: /Yesterday 1 task/ })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Filed taxes')).toBeVisible()

    const week = screen.getByRole('button', { name: /Earlier this week 1 task/ })
    const september = screen.getByRole('button', { name: /September 2026 1 task/ })
    expect(week).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Fixed the bike')).not.toBeInTheDocument()

    // Keyboard: focus the header and press Enter.
    september.focus()
    await userEvent.keyboard('{Enter}')
    expect(september).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Fixed the bike')).toBeVisible()

    await userEvent.click(today)
    expect(today).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Filed taxes')).not.toBeInTheDocument()
  })

  it('keeps Reopen and the actions menu inside a group', async () => {
    stubFetch(() => json([done('Old report', '2026-08-03T08:00:00Z')]))
    renderWithApp(<TasksPage />, { route: '/tasks?status=completed' })

    await userEvent.click(await screen.findByRole('button', { name: /August 2026/ }))
    const group = screen.getByRole('list', { name: 'Completed august 2026' })
    expect(within(group).getByRole('button', { name: 'Reopen' })).toBeEnabled()
    await userEvent.click(within(group).getByRole('button', { name: 'Actions for “Old report”' }))
    expect(await screen.findByRole('menuitem', { name: 'Delete…' })).toBeInTheDocument()
  })
})

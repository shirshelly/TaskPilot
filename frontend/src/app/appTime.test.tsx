import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { queryKeys } from '@/queries/queries'
import { AppTimeProvider, useAppTime } from './appTime'

function ShowToday() {
  return <p>{useAppTime().today}</p>
}

afterEach(() => vi.useRealTimers())

describe('AppTimeProvider', () => {
  it('rolls "today" over at midnight in the app zone and refreshes all task data', () => {
    vi.useFakeTimers()
    // 23:59:30 on 20 Oct in Jerusalem (UTC+3) — still 20 Oct, 20:59 in UTC.
    vi.setSystemTime(new Date('2026-10-20T20:59:30Z'))
    const queryClient = new QueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    render(
      <QueryClientProvider client={queryClient}>
        <AppTimeProvider zone="Asia/Jerusalem">
          <ShowToday />
        </AppTimeProvider>
      </QueryClientProvider>,
    )
    expect(screen.getByText('2026-10-20')).toBeInTheDocument()
    expect(invalidate).not.toHaveBeenCalled()

    act(() => vi.advanceTimersByTime(60_000))

    expect(screen.getByText('2026-10-21')).toBeInTheDocument()
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.root })
  })
})

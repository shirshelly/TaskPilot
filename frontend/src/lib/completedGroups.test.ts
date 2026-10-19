import { describe, expect, it } from 'vitest'
import { task } from '@/test/render'
import { groupCompletedTasks, startOfWeek } from './completedGroups'

const JERUSALEM = 'Asia/Jerusalem'
const done = (title: string, completedAt: string) => task({ title, status: 'COMPLETED', completedAt })
const summary = (groups: ReturnType<typeof groupCompletedTasks>) =>
  groups.map((g) => [g.label, g.tasks.map((t) => t.title), g.defaultOpen])

describe('startOfWeek (Monday)', () => {
  it('goes back to Monday, and Monday is its own start', () => {
    expect(startOfWeek('2026-10-21')).toBe('2026-10-19') // Wed
    expect(startOfWeek('2026-10-19')).toBe('2026-10-19') // Mon
    expect(startOfWeek('2026-10-25')).toBe('2026-10-19') // Sun ends the week
    expect(startOfWeek('2026-11-01')).toBe('2026-10-26') // across a month boundary
  })
})

describe('groupCompletedTasks', () => {
  // Newest first, as the backend returns them. Today is Wednesday 21 Oct 2026 in Jerusalem (UTC+3).
  const tasks = [
    done('Today, after local midnight', '2026-10-20T21:30:00Z'), // 21 Oct 00:30 local
    done('Yesterday, late evening', '2026-10-20T20:30:00Z'), // 20 Oct 23:30 local
    done('Monday', '2026-10-19T09:00:00Z'),
    done('Last Sunday', '2026-10-18T09:00:00Z'),
    done('Start of October', '2026-10-01T09:00:00Z'),
    done('End of September', '2026-09-30T20:59:00Z'), // 23:59 local, still September
    done('Last year', '2025-12-31T10:00:00Z'),
  ]

  it('groups by app-zone completion date, newest groups first, order kept inside groups', () => {
    expect(summary(groupCompletedTasks(tasks, JERUSALEM, '2026-10-21'))).toEqual([
      ['Today', ['Today, after local midnight'], true],
      ['Yesterday', ['Yesterday, late evening'], true],
      ['Earlier this week', ['Monday'], false],
      ['October 2026', ['Last Sunday', 'Start of October'], false],
      ['September 2026', ['End of September'], false],
      ['December 2025', ['Last year'], false],
    ])
  })

  it('places every task exactly once', () => {
    const grouped = groupCompletedTasks(tasks, JERUSALEM, '2026-10-21').flatMap((g) => g.tasks)
    expect(grouped).toHaveLength(tasks.length)
    expect(new Set(grouped)).toEqual(new Set(tasks))
  })

  it('uses the application zone, not UTC', () => {
    // In UTC this would be "Yesterday"; in Jerusalem it is already today.
    expect(summary(groupCompletedTasks(tasks.slice(0, 1), 'UTC', '2026-10-21'))[0][0]).toBe('Yesterday')
  })

  it('on a Monday, yesterday is Sunday and nothing is "earlier this week"', () => {
    const onMonday = [done('Sunday', '2026-10-25T09:00:00Z'), done('Saturday', '2026-10-24T09:00:00Z')]
    expect(summary(groupCompletedTasks(onMonday, JERUSALEM, '2026-10-26'))).toEqual([
      ['Yesterday', ['Sunday'], true],
      ['October 2026', ['Saturday'], false],
    ])
  })

  it('omits empty groups and handles no tasks', () => {
    expect(groupCompletedTasks([], JERUSALEM, '2026-10-21')).toEqual([])
    expect(summary(groupCompletedTasks([done('Old', '2026-08-01T09:00:00Z')], JERUSALEM, '2026-10-21'))).toEqual([
      ['August 2026', ['Old'], false],
    ])
  })
})

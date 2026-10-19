import type { Task } from '@/api/types'
import { addDays, zonedParts, type IsoDate } from './time'

/**
 * Presentation-only grouping of completed tasks by the date they were completed, in the
 * application zone. Checks run in order, so every task lands in exactly one group:
 *
 *   Today              completed today (a completedAt "in the future" — clock skew — counts as today)
 *   Yesterday          completed yesterday, even when that was in the previous week
 *   Earlier this week  from the start of this week up to the day before yesterday
 *   <Month Year>       everything older, by calendar month (incl. this month before this week)
 *
 * Weeks start on Monday (ISO 8601). On a Monday or Tuesday "Earlier this week" is empty.
 * Order within a group is the backend's (most recently completed first); empty groups are omitted.
 */
export const WEEK_STARTS_ON = 1 // 0 = Sunday … 6 = Saturday

export interface CompletedGroup {
  key: string
  label: string
  tasks: Task[]
  /** Recent groups start expanded; older ones start collapsed. */
  defaultOpen: boolean
}

const monthLabel = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' })

export function startOfWeek(today: IsoDate): IsoDate {
  const weekday = new Date(`${today}T00:00:00Z`).getUTCDay()
  return addDays(today, -((weekday - WEEK_STARTS_ON + 7) % 7))
}

export function groupCompletedTasks(tasks: Task[], zone: string, today: IsoDate): CompletedGroup[] {
  const yesterday = addDays(today, -1)
  const weekStart = startOfWeek(today)
  const groups = new Map<string, CompletedGroup>()

  const add = (key: string, label: string, defaultOpen: boolean, task: Task) => {
    const group = groups.get(key) ?? { key, label, tasks: [], defaultOpen }
    group.tasks.push(task)
    groups.set(key, group)
  }

  for (const task of tasks) {
    // completedAt is guaranteed for COMPLETED tasks by the backend; guard anyway.
    if (!task.completedAt) {
      add('undated', 'Undated', false, task)
      continue
    }
    const date = zonedParts(task.completedAt, zone).date
    if (date >= today) add('today', 'Today', true, task)
    else if (date === yesterday) add('yesterday', 'Yesterday', true, task)
    else if (date >= weekStart) add('this-week', 'Earlier this week', false, task)
    else add(`month-${date.slice(0, 7)}`, monthLabel.format(new Date(`${date.slice(0, 7)}-01T00:00:00Z`)), false, task)
  }

  const rank = (key: string) =>
    key === 'today' ? 0 : key === 'yesterday' ? 1 : key === 'this-week' ? 2 : key === 'undated' ? 4 : 3
  return [...groups.values()].sort((a, b) => rank(a.key) - rank(b.key) || b.key.localeCompare(a.key))
}

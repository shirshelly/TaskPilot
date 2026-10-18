import type { TaskStatus } from '@/api/types'

/** Tasks page filters, in display order. Stored lowercase in ?status=; default Active. */
export const TASK_FILTERS = ['all', 'active', 'scheduled', 'completed'] as const
export type TaskFilter = (typeof TASK_FILTERS)[number]

export const DEFAULT_TASK_FILTER: TaskFilter = 'active'
export const STATUS_PARAM = 'status'

export const TASK_FILTER_LABELS: Record<TaskFilter, string> = {
  all: 'All',
  active: 'Active',
  scheduled: 'Scheduled',
  completed: 'Completed',
}

export function parseTaskFilter(raw: string | null): TaskFilter | null {
  return (TASK_FILTERS as readonly string[]).includes(raw ?? '') ? (raw as TaskFilter) : null
}

/** The backend's ?status= value; null for All (no filter). */
export function statusForFilter(filter: TaskFilter): TaskStatus | null {
  return filter === 'all' ? null : (filter.toUpperCase() as TaskStatus)
}

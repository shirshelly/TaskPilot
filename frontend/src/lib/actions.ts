import type { Task } from '@/api/types'

export interface TaskActions {
  canComplete: boolean
  canReopen: boolean
  canSchedule: boolean
  scheduleLabel: 'Schedule' | 'Reschedule'
}

/**
 * Which lifecycle actions to offer for a task. Mirrors the backend state machine only to hide
 * actions that would certainly be rejected; the server still validates every request (a stale
 * view can always get a 409, which the UI handles).
 */
export function actionsFor(task: Pick<Task, 'status' | 'scheduledFor'>): TaskActions {
  const open = task.status !== 'COMPLETED'
  return {
    canComplete: open,
    canReopen: !open,
    canSchedule: open,
    scheduleLabel: task.status === 'SCHEDULED' || task.scheduledFor !== null ? 'Reschedule' : 'Schedule',
  }
}

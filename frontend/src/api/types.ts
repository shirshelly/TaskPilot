// Mirrors the backend DTOs (TaskResponse, GET /app-info). Dates stay strings: `deadline` is a
// LocalDate ('YYYY-MM-DD'); `scheduledFor` and the lifecycle timestamps are absolute instants.

export type TaskStatus = 'ACTIVE' | 'SCHEDULED' | 'COMPLETED'

export interface Task {
  id: string
  title: string
  description: string | null
  status: TaskStatus
  importance: number
  /** Total time to finish the task. */
  estimatedDurationMinutes: number | null
  /** Shortest useful sitting, for work spread over several sessions. */
  minSessionMinutes: number | null
  deadline: string | null
  scheduledFor: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
  /** Computed by the backend; the frontend never derives it. */
  isOverdue: boolean
  version: number
}

/** POST /tasks. Status is never sent: the backend derives it from scheduledFor. */
export interface CreateTaskRequest {
  title: string
  description?: string
  importance?: number
  estimatedDurationMinutes?: number
  minSessionMinutes?: number
  deadline?: string
  scheduledFor?: string
}

/**
 * PATCH /tasks/{id}: an omitted key leaves the field unchanged, an explicit null clears it.
 * Lifecycle fields (status, scheduledFor, completedAt) are not part of the type, so they
 * can't be sent by mistake; scheduling goes through POST /tasks/{id}/schedule.
 */
export interface TaskPatch {
  title?: string
  description?: string | null
  importance?: number
  estimatedDurationMinutes?: number | null
  minSessionMinutes?: number | null
  deadline?: string | null
}

export interface AppInfo {
  zoneId: string
}

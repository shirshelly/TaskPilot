// Mirrors the backend DTOs (TaskResponse, GET /app-info). Dates stay strings: `deadline` is a
// LocalDate ('YYYY-MM-DD'); `scheduledFor` and the lifecycle timestamps are absolute instants.

export type TaskStatus = 'ACTIVE' | 'SCHEDULED' | 'COMPLETED'

export interface Task {
  id: string
  title: string
  description: string | null
  status: TaskStatus
  importance: number
  estimatedDurationMinutes: number | null
  deadline: string | null
  scheduledFor: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
  /** Computed by the backend; the frontend never derives it. */
  isOverdue: boolean
  version: number
}

export interface AppInfo {
  zoneId: string
}

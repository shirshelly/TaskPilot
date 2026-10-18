import { apiGet, apiRequest } from './http'
import type { AppInfo, CreateTaskRequest, Task, TaskPatch, TaskStatus } from './types'

// One function per backend endpoint. List order is the backend's and is never re-sorted here.

export function fetchAppInfo(signal?: AbortSignal): Promise<AppInfo> {
  return apiGet<AppInfo>('/app-info', undefined, signal)
}

export function fetchTasks(status: TaskStatus | null, signal?: AbortSignal): Promise<Task[]> {
  return apiGet<Task[]>('/tasks', { status }, signal)
}

export function fetchTask(id: string, signal?: AbortSignal): Promise<Task> {
  return apiGet<Task>(`/tasks/${id}`, undefined, signal)
}

export function fetchAgenda(signal?: AbortSignal): Promise<Task[]> {
  return apiGet<Task[]>('/agenda/today', undefined, signal)
}

export function fetchRecommendations(availableMinutes: number | null, signal?: AbortSignal): Promise<Task[]> {
  return apiGet<Task[]>('/recommendations', { availableMinutes }, signal)
}

export function createTask(request: CreateTaskRequest): Promise<Task> {
  return apiRequest<Task>('POST', '/tasks', { body: request })
}

/** Conditional update: the backend answers 412 VERSION_CONFLICT if `expectedVersion` is stale. */
export function updateTask(id: string, patch: TaskPatch, expectedVersion: number): Promise<Task> {
  return apiRequest<Task>('PATCH', `/tasks/${id}`, {
    body: patch,
    headers: { 'If-Match': `"${expectedVersion}"` },
  })
}

export function completeTask(id: string): Promise<Task> {
  return apiRequest<Task>('POST', `/tasks/${id}/complete`)
}

export function reopenTask(id: string): Promise<Task> {
  return apiRequest<Task>('POST', `/tasks/${id}/reopen`)
}

export function scheduleTask(id: string, scheduledFor: string): Promise<Task> {
  return apiRequest<Task>('POST', `/tasks/${id}/schedule`, { body: { scheduledFor } })
}

export function deleteTask(id: string): Promise<void> {
  return apiRequest<void>('DELETE', `/tasks/${id}`)
}

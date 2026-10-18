import { apiGet } from './http'
import type { AppInfo, Task, TaskStatus } from './types'

// One function per backend endpoint. List order is the backend's and is never re-sorted here.

export function fetchAppInfo(signal?: AbortSignal): Promise<AppInfo> {
  return apiGet<AppInfo>('/app-info', undefined, signal)
}

export function fetchTasks(status: TaskStatus | null, signal?: AbortSignal): Promise<Task[]> {
  return apiGet<Task[]>('/tasks', { status }, signal)
}

export function fetchAgenda(signal?: AbortSignal): Promise<Task[]> {
  return apiGet<Task[]>('/agenda/today', undefined, signal)
}

export function fetchRecommendations(availableMinutes: number | null, signal?: AbortSignal): Promise<Task[]> {
  return apiGet<Task[]>('/recommendations', { availableMinutes }, signal)
}

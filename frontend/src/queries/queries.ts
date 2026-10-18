import { keepPreviousData, QueryClient, useQuery } from '@tanstack/react-query'
import { fetchAgenda, fetchAppInfo, fetchRecommendations, fetchTask, fetchTasks } from '@/api/endpoints'
import type { Task } from '@/api/types'
import { ApiError } from '@/api/errors'
import { statusForFilter, type TaskFilter } from '@/lib/taskFilter'

/**
 * Every task-derived query lives under the `tp` root so one invalidation refreshes them all.
 * Agenda membership, overdue flags and ranking are computed by the backend from data the
 * client can't fully reason about, so after a change we refetch rather than patch caches.
 */
export const queryKeys = {
  appInfo: ['app-info'] as const,
  root: ['tp'] as const,
  tasks: (filter: TaskFilter) => ['tp', 'tasks', filter] as const,
  task: (id: string) => ['tp', 'task', id] as const,
  agenda: () => ['tp', 'agenda'] as const,
  recommendations: (minutes: number | null) => ['tp', 'recommendations', minutes ?? 'any'] as const,
}

/**
 * Views on screen poll so the background activation worker's changes show up without a reload.
 * TanStack Query only polls mounted queries and pauses while the tab is hidden
 * (refetchIntervalInBackground defaults to false); focus triggers a refetch of stale data.
 */
export const LIVE_REFRESH_MS = 60_000

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        // A 4xx won't fix itself on retry; network and 5xx failures get two more attempts.
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
      },
    },
  })
}

export function useAppInfoQuery() {
  return useQuery({
    queryKey: queryKeys.appInfo,
    queryFn: ({ signal }) => fetchAppInfo(signal),
    staleTime: Infinity,
  })
}

export function useAgenda() {
  return useQuery({
    queryKey: queryKeys.agenda(),
    queryFn: ({ signal }) => fetchAgenda(signal),
    refetchInterval: LIVE_REFRESH_MS,
  })
}

export function useRecommendations(minutes: number | null) {
  return useQuery({
    queryKey: queryKeys.recommendations(minutes),
    queryFn: ({ signal }) => fetchRecommendations(minutes, signal),
    refetchInterval: LIVE_REFRESH_MS,
    // Switching the time filter keeps the previous list on screen (dimmed) instead of flashing a skeleton.
    placeholderData: keepPreviousData,
  })
}

export function useTasks(filter: TaskFilter) {
  return useQuery({
    queryKey: queryKeys.tasks(filter),
    queryFn: ({ signal }) => fetchTasks(statusForFilter(filter), signal),
    refetchInterval: LIVE_REFRESH_MS,
  })
}

/**
 * One task, for the edit sheet. Seeded with the row's data but marked stale, so opening the
 * sheet always confirms the current server version (and its `version`) in the background.
 */
export function useTask(task: Task) {
  return useQuery({
    queryKey: queryKeys.task(task.id),
    queryFn: ({ signal }) => fetchTask(task.id, signal),
    initialData: task,
    initialDataUpdatedAt: 0,
    refetchOnMount: 'always',
  })
}

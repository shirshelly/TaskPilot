import { useIsMutating, useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query'
import {
  completeTask,
  createTask,
  deleteTask,
  reopenTask,
  scheduleTask,
  updateTask,
} from '@/api/endpoints'
import { ApiError } from '@/api/errors'
import type { CreateTaskRequest, Task, TaskPatch } from '@/api/types'
import { queryKeys } from './queries'

/**
 * Server-confirmed mutations: nothing changes on screen until the backend answers. On success
 * the returned task is written to its detail cache and every task view is refetched — Agenda
 * membership, overdue flags and ranking are backend decisions the client doesn't predict.
 * The invalidation is awaited, so the caller's pending state lasts until lists are fresh
 * (a completed row doesn't vanish and reappear).
 *
 * When the server says our view was stale (404, 409, 412), task views are refetched too, so
 * the screen catches up with what actually happened.
 */
async function refreshAfter(queryClient: QueryClient, task: Task | null) {
  if (task) queryClient.setQueryData(queryKeys.task(task.id), task)
  await queryClient.invalidateQueries({ queryKey: queryKeys.root })
}

function isStaleViewError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === 'TASK_NOT_FOUND' || error.code === 'INVALID_STATE_TRANSITION' || error.code === 'VERSION_CONFLICT')
  )
}

/** Every mutation of one task shares this key, so any view can ask "is this task busy?". */
const taskMutationKey = (taskId: string) => ['task-mutation', taskId] as const

export function useIsTaskBusy(taskId: string): boolean {
  return useIsMutating({ mutationKey: taskMutationKey(taskId) }) > 0
}

/**
 * Callbacks that must run even if the calling component unmounts first — e.g. a completed
 * row that leaves the Agenda during the awaited refetch. (Callbacks passed to `mutate()` only
 * run while the component that called it is still mounted.)
 */
export interface MutationCallbacks<TResult> {
  onSuccess?: (result: TResult) => void
  onError?: (error: unknown) => void
}

function useTaskMutation<TVariables, TResult extends Task | void>(
  taskId: string | null,
  mutationFn: (variables: TVariables) => Promise<TResult>,
  callbacks: MutationCallbacks<TResult> = {},
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationKey: taskId ? taskMutationKey(taskId) : ['task-mutation', 'new'],
    mutationFn,
    onSuccess: async (result) => {
      await refreshAfter(queryClient, result ? (result as Task) : null)
      callbacks.onSuccess?.(result)
    },
    onError: (error) => {
      if (isStaleViewError(error)) void queryClient.invalidateQueries({ queryKey: queryKeys.root })
      callbacks.onError?.(error)
    },
  })
}

export const useCreateTask = () => useTaskMutation(null, (request: CreateTaskRequest) => createTask(request))

/** PATCH with If-Match: `expectedVersion` is the version the edit was based on. */
export const useUpdateTask = (taskId: string) =>
  useTaskMutation(taskId, ({ patch, expectedVersion }: { patch: TaskPatch; expectedVersion: number }) =>
    updateTask(taskId, patch, expectedVersion),
  )

export const useCompleteTask = (taskId: string, callbacks?: MutationCallbacks<Task>) =>
  useTaskMutation(taskId, () => completeTask(taskId), callbacks)

export const useReopenTask = (taskId: string, callbacks?: MutationCallbacks<Task>) =>
  useTaskMutation(taskId, () => reopenTask(taskId), callbacks)

export const useScheduleTask = (taskId: string) =>
  useTaskMutation(taskId, (scheduledFor: string) => scheduleTask(taskId, scheduledFor))

/** A task already gone (404) counts as deleted: the user's intent is fulfilled either way. */
export function useDeleteTask(taskId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationKey: taskMutationKey(taskId),
    mutationFn: async () => {
      try {
        await deleteTask(taskId)
        return { alreadyGone: false }
      } catch (error) {
        if (error instanceof ApiError && error.code === 'TASK_NOT_FOUND') return { alreadyGone: true }
        throw error
      }
    },
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: queryKeys.task(taskId) })
      await queryClient.invalidateQueries({ queryKey: queryKeys.root })
    },
  })
}

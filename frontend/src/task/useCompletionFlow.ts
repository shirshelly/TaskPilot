import { toast } from 'sonner'
import type { Task } from '@/api/types'
import { useTaskOverlays } from '@/app/taskOverlays'
import { describeActionError } from '@/lib/errorMessages'
import { useCompleteTask, useIsTaskBusy, type MutationCallbacks } from '@/queries/mutations'

/*
 * Session-aware completion (V1 UX convention, no backend concept): a task with a minimum
 * session is worked on in sittings, so finishing a sitting must not complete it by accident.
 * Every "complete" control goes through useCompletionFlow, which asks first for such tasks.
 * "I'm done for now" records nothing — there is no time tracking in V1.
 */

export const completionFeedback: MutationCallbacks<Task> = {
  onSuccess: (done) => toast.success(`Completed “${done.title}”`),
  onError: (error) => toast.error(describeActionError(error)),
}

/** The single entry point for completing a task from the UI. */
export function useCompletionFlow(task: Task) {
  const overlays = useTaskOverlays()
  const complete = useCompleteTask(task.id, completionFeedback)
  const busy = useIsTaskBusy(task.id)
  const asksFirst = task.minSessionMinutes !== null

  const start = () => {
    if (busy || task.status === 'COMPLETED') return
    if (asksFirst) overlays.openCompletionChoice(task)
    else complete.mutate()
  }

  return { start, busy, asksFirst, isPending: complete.isPending }
}

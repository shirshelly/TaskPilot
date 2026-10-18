import { Check, Loader2, MoreHorizontal } from 'lucide-react'
import { toast } from 'sonner'
import type { Task } from '@/api/types'
import { useTaskOverlays } from '@/app/taskOverlays'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { actionsFor } from '@/lib/actions'
import { describeActionError } from '@/lib/errorMessages'
import { cn } from '@/lib/utils'
import { useCompleteTask, useIsTaskBusy, useReopenTask } from '@/queries/mutations'
import { TaskMeta } from './TaskMeta'

/**
 * One calm list row, shared by Agenda, Focus alternatives and the Tasks page. The circle
 * completes, the body opens the editor, and ⋯ holds the rest. Each row owns its mutations, so
 * a pending action only affects this row.
 */
export function TaskRow({ task, showStatus = false }: { task: Task; showStatus?: boolean }) {
  const overlays = useTaskOverlays()
  const busy = useIsTaskBusy(task.id)
  const completed = task.status === 'COMPLETED'

  return (
    <li aria-busy={busy} className={cn('group flex items-start gap-3 py-3 transition-opacity', busy && 'opacity-60')}>
      <CompleteToggle task={task} className="mt-[3px]" />
      <button
        type="button"
        onClick={() => overlays.openEdit(task)}
        className="-my-1 min-w-0 flex-1 rounded-md py-1 text-left outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
      >
        <span
          className={cn(
            'block text-[15px] leading-snug font-medium break-words',
            completed && 'text-muted-foreground line-through decoration-foreground/25',
          )}
        >
          {task.title}
        </span>
        <TaskMeta task={task} showStatus={showStatus} className="mt-1" />
      </button>
      {completed && <ReopenButton task={task} />}
      <TaskMenu task={task} />
    </li>
  )
}

/** Round checkbox. Completing is server-confirmed: it spins until the lists have refreshed. */
export function CompleteToggle({ task, className }: { task: Task; className?: string }) {
  // Callbacks on the hook, not on mutate(): the row may leave the list before the request settles.
  const complete = useCompleteTask(task.id, {
    onSuccess: (done) => toast.success(`Completed “${done.title}”`),
    onError: (error) => toast.error(describeActionError(error)),
  })
  const busy = useIsTaskBusy(task.id)
  const completed = task.status === 'COMPLETED'

  const onClick = () => {
    if (busy || completed) return
    complete.mutate()
  }

  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={completed}
      aria-label={completed ? `“${task.title}” is completed` : `Complete “${task.title}”`}
      disabled={completed || busy}
      onClick={onClick}
      className={cn(
        'group/check grid size-[18px] shrink-0 place-items-center rounded-full border-[1.5px] outline-none transition-colors',
        'focus-visible:ring-[3px] focus-visible:ring-ring/50',
        completed
          ? 'border-brand bg-brand text-primary-foreground'
          : 'border-foreground/30 hover:border-brand hover:bg-brand-soft disabled:hover:bg-transparent',
        task.status === 'SCHEDULED' && 'border-dashed',
        className,
      )}
    >
      {complete.isPending ? (
        <Loader2 className="size-3 animate-spin text-brand" aria-hidden="true" />
      ) : (
        <Check
          className={cn('size-3', !completed && 'text-brand opacity-0 group-hover/check:opacity-100')}
          strokeWidth={3}
          aria-hidden="true"
        />
      )}
    </button>
  )
}

function ReopenButton({ task }: { task: Task }) {
  const reopen = useReopenTask(task.id, {
    onSuccess: (reopened) => toast.success(`Reopened “${reopened.title}”`),
    onError: (error) => toast.error(describeActionError(error)),
  })
  const busy = useIsTaskBusy(task.id)
  return (
    <Button
      variant="ghost"
      size="sm"
      className="text-muted-foreground"
      disabled={busy}
      onClick={() => reopen.mutate()}
    >
      {reopen.isPending && <Loader2 className="animate-spin" aria-hidden="true" />}
      Reopen
    </Button>
  )
}

function TaskMenu({ task }: { task: Task }) {
  const overlays = useTaskOverlays()
  const busy = useIsTaskBusy(task.id)
  const actions = actionsFor(task)
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon-sm"
          className="text-muted-foreground sm:opacity-0 sm:group-hover:opacity-100 sm:focus-visible:opacity-100 sm:data-[state=open]:opacity-100"
          aria-label={`Actions for “${task.title}”`}
          disabled={busy}
        >
          <MoreHorizontal aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-44">
        <DropdownMenuItem onSelect={() => overlays.openEdit(task)}>Edit</DropdownMenuItem>
        {actions.canSchedule && (
          <DropdownMenuItem onSelect={() => overlays.openSchedule(task)}>{actions.scheduleLabel}…</DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onSelect={() => overlays.openDelete(task)}>
          Delete…
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

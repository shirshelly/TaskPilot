import { Check } from 'lucide-react'
import type { Task, TaskStatus } from '@/api/types'
import { cn } from '@/lib/utils'
import { TaskMeta } from './TaskMeta'

/** One calm list row, shared by Agenda, Focus alternatives, and the Tasks page. */
export function TaskRow({ task, showStatus = false }: { task: Task; showStatus?: boolean }) {
  const completed = task.status === 'COMPLETED'
  return (
    <li className="flex gap-3 py-3.5">
      <StatusMark status={task.status} />
      <div className="min-w-0 flex-1">
        <p
          className={cn(
            'text-[15px] leading-snug font-medium break-words',
            completed && 'text-muted-foreground line-through decoration-foreground/25',
          )}
        >
          {task.title}
        </p>
        <TaskMeta task={task} showStatus={showStatus} className="mt-1" />
      </div>
    </li>
  )
}

/**
 * Visual only for now (the interactive checkbox arrives with mutations). A dashed ring marks a
 * SCHEDULED task — present but not actionable yet.
 */
function StatusMark({ status }: { status: TaskStatus }) {
  if (status === 'COMPLETED') {
    return (
      <span aria-hidden="true" className="mt-0.5 grid size-[18px] shrink-0 place-items-center rounded-full bg-brand text-primary-foreground">
        <Check className="size-3" strokeWidth={3} />
      </span>
    )
  }
  return (
    <span
      aria-hidden="true"
      className={cn(
        'mt-0.5 size-[18px] shrink-0 rounded-full border-[1.5px] border-foreground/30',
        status === 'SCHEDULED' && 'border-dashed',
      )}
    />
  )
}

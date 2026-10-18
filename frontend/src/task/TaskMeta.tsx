import type { ReactNode } from 'react'
import { CalendarClock, Check, ChevronsUp, Clock3, Flag } from 'lucide-react'
import type { Task } from '@/api/types'
import { useAppTime } from '@/app/appTime'
import {
  formatCompleted,
  formatDeadline,
  formatDuration,
  formatSchedule,
  importanceLabel,
  inSentence,
} from '@/lib/format'
import { cn } from '@/lib/utils'

/** Secondary line of a task: overdue, Start on, Due by, duration, notable importance. */
export function TaskMeta({ task, showStatus = false, className }: { task: Task; showStatus?: boolean; className?: string }) {
  const { zone, today } = useAppTime()
  const items: ReactNode[] = []

  if (task.status === 'COMPLETED' && task.completedAt) {
    items.push(<Item key="done" icon={<Check />}>{formatCompleted(task.completedAt, zone, today)}</Item>)
  } else if (task.isOverdue) {
    items.push(
      <span key="overdue" className="inline-flex items-center gap-1.5 font-medium text-overdue">
        <span aria-hidden="true" className="size-1.5 rounded-full bg-overdue" />
        Overdue
      </span>,
    )
  }
  if (showStatus && task.status === 'SCHEDULED') {
    items.push(<span key="status" className="font-medium text-foreground/70">Scheduled</span>)
  }
  if (task.scheduledFor) {
    const label = formatSchedule(task.scheduledFor, zone, today)
    items.push(
      <Item key="start" icon={<CalendarClock />} title="Start on">
        {task.status === 'SCHEDULED' ? `Starts ${inSentence(label)}` : label}
      </Item>,
    )
  }
  if (task.deadline) {
    items.push(<Item key="due" icon={<Flag />} title="Due by">{formatDeadline(task.deadline, today)}</Item>)
  }
  if (task.estimatedDurationMinutes !== null) {
    items.push(
      <Item key="duration" icon={<Clock3 />} title="Estimated duration">
        {formatDuration(task.estimatedDurationMinutes)}
      </Item>,
    )
  }
  const importance = importanceLabel(task.importance)
  if (importance) {
    items.push(<Item key="importance" icon={<ChevronsUp />}>{importance}</Item>)
  }

  if (items.length === 0) return null
  return (
    <div className={cn('flex flex-wrap items-center gap-x-3.5 gap-y-1 text-[13px] text-muted-foreground', className)}>
      {items}
    </div>
  )
}

function Item({ icon, title, children }: { icon: ReactNode; title?: string; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 [&_svg]:size-3.5 [&_svg]:shrink-0" title={title}>
      <span aria-hidden="true" className="inline-flex">{icon}</span>
      {title && <span className="sr-only">{title}: </span>}
      {children}
    </span>
  )
}

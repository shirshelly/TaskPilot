import { useState } from 'react'
import { ChevronRight } from 'lucide-react'
import type { Task } from '@/api/types'
import { useAppTime } from '@/app/appTime'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { groupCompletedTasks } from '@/lib/completedGroups'
import { cn } from '@/lib/utils'
import { TaskRow } from './TaskRow'

/** Completed tasks in collapsible date groups (Today, Yesterday, Earlier this week, months). */
export function CompletedGroups({ tasks }: { tasks: Task[] }) {
  const { zone, today } = useAppTime()
  const groups = groupCompletedTasks(tasks, zone, today)
  // Only explicit toggles are stored; untouched groups follow their default.
  const [toggled, setToggled] = useState<Record<string, boolean>>({})

  return (
    <div className="divide-y divide-border/70 border-y border-border/70">
      {groups.map((group) => {
        const open = toggled[group.key] ?? group.defaultOpen
        return (
          <Collapsible
            key={group.key}
            open={open}
            onOpenChange={(next) => setToggled((current) => ({ ...current, [group.key]: next }))}
          >
            <h2>
              <CollapsibleTrigger asChild>
                <button
                  type="button"
                  className="flex min-h-11 w-full items-center gap-2 rounded-md py-2.5 text-left outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
                >
                  <ChevronRight
                    aria-hidden="true"
                    className={cn('size-4 text-muted-foreground transition-transform', open && 'rotate-90')}
                  />
                  <span className="text-sm font-medium">{group.label}</span>{' '}
                  <span className="text-sm text-muted-foreground tabular-nums">
                    {group.tasks.length}{' '}
                    <span className="sr-only">{group.tasks.length === 1 ? 'task' : 'tasks'}</span>
                  </span>
                </button>
              </CollapsibleTrigger>
            </h2>
            <CollapsibleContent>
              <ul aria-label={`Completed ${group.label.toLowerCase()}`} className="divide-y divide-border/70 pb-1 pl-6">
                {group.tasks.map((task) => (
                  <TaskRow key={task.id} task={task} />
                ))}
              </ul>
            </CollapsibleContent>
          </Collapsible>
        )
      })}
    </div>
  )
}

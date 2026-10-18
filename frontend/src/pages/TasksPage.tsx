import { useEffect } from 'react'
import { useSearchParams } from 'react-router'
import { useDocumentTitle } from '@/app/useDocumentTitle'
import { EmptyState, ErrorState, ListSkeleton, RefreshFailedNote } from '@/components/states'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import {
  DEFAULT_TASK_FILTER,
  parseTaskFilter,
  STATUS_PARAM,
  TASK_FILTER_LABELS,
  TASK_FILTERS,
  type TaskFilter,
} from '@/lib/taskFilter'
import { useTasks } from '@/queries/queries'
import { TaskRow } from '@/task/TaskRow'

const EMPTY_COPY: Record<TaskFilter, { title: string; description: string }> = {
  all: { title: 'No tasks yet.', description: 'Tasks you add will appear here.' },
  active: { title: 'No active tasks.', description: 'Scheduled tasks become active on their start date.' },
  scheduled: { title: 'Nothing scheduled.', description: 'Tasks with a future start date wait here until then.' },
  completed: { title: 'No completed tasks yet.', description: 'Finished tasks are listed here, most recent first.' },
}

export function TasksPage() {
  useDocumentTitle('Tasks')
  const [searchParams, setSearchParams] = useSearchParams()
  const rawStatus = searchParams.get(STATUS_PARAM)
  const filter = parseTaskFilter(rawStatus) ?? DEFAULT_TASK_FILTER
  const query = useTasks(filter)

  const setFilter = (next: TaskFilter) =>
    setSearchParams(
      (previous) => {
        const params = new URLSearchParams(previous)
        if (next === DEFAULT_TASK_FILTER) params.delete(STATUS_PARAM)
        else params.set(STATUS_PARAM, next)
        return params
      },
      { replace: true },
    )

  // Unknown ?status= values fall back to the default; keep the URL honest about that.
  useEffect(() => {
    if (rawStatus !== null && parseTaskFilter(rawStatus) === null) setFilter(DEFAULT_TASK_FILTER)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rawStatus])

  return (
    <>
      <header className="flex flex-col gap-5 pt-8 pb-6 sm:flex-row sm:items-end sm:justify-between sm:pt-12 sm:pb-8">
        <h1 className="font-serif text-5xl leading-none tracking-tight sm:text-6xl">Tasks</h1>
        <ToggleGroup
          type="single"
          variant="outline"
          spacing={0}
          aria-label="Filter tasks by status"
          value={filter}
          onValueChange={(value) => value && setFilter(value as TaskFilter)}
          className="bg-card"
        >
          {TASK_FILTERS.map((option) => (
            <ToggleGroupItem
              key={option}
              value={option}
              className="h-8 px-3.5 text-[13px] data-[state=on]:bg-foreground data-[state=on]:text-background"
            >
              {TASK_FILTER_LABELS[option]}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
      </header>

      <section aria-label={`${TASK_FILTER_LABELS[filter]} tasks`} className="max-w-3xl">
        {query.isError && query.data && (
          <div className="mb-2">
            <RefreshFailedNote onRetry={() => void query.refetch()} />
          </div>
        )}
        {query.isPending ? (
          <ListSkeleton rows={5} />
        ) : query.isError && !query.data ? (
          <ErrorState error={query.error} onRetry={() => void query.refetch()} />
        ) : query.data.length === 0 ? (
          <EmptyState {...EMPTY_COPY[filter]} />
        ) : (
          <ul className="divide-y divide-border/70 border-y border-border/70">
            {query.data.map((task) => (
              <TaskRow key={task.id} task={task} showStatus={filter === 'all'} />
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

import { useAppTime } from '@/app/appTime'
import { useDocumentTitle } from '@/app/useDocumentTitle'
import { EmptyState, ErrorState, ListSkeleton, RefreshFailedNote, SectionHeader } from '@/components/states'
import { formatDayHeading } from '@/lib/format'
import { useAgenda } from '@/queries/queries'
import { FocusPanel } from '@/task/FocusPanel'
import { TaskRow } from '@/task/TaskRow'

/**
 * Agenda first ("What do I have today?"), Focus beside it on wide screens and below it on
 * narrow ones ("What should I focus on right now?").
 */
export function TodayPage() {
  useDocumentTitle('Today')
  const { today } = useAppTime()
  const heading = formatDayHeading(today)

  return (
    <>
      <header className="pt-8 pb-8 sm:pt-12 sm:pb-10">
        <h1 className="font-serif text-5xl leading-none tracking-tight sm:text-6xl">
          <span className="sr-only">Today, </span>
          {heading.weekday}
        </h1>
        <p className="mt-2 text-muted-foreground">{heading.date}</p>
      </header>

      <div className="grid gap-12 lg:grid-cols-12 lg:gap-16">
        <AgendaSection className="min-w-0 lg:col-span-7" />
        <FocusPanel className="min-w-0 lg:sticky lg:top-24 lg:col-span-5 lg:self-start" />
      </div>
    </>
  )
}

function AgendaSection({ className }: { className?: string }) {
  const query = useAgenda()

  return (
    <section aria-labelledby="agenda-heading" className={className}>
      <SectionHeader id="agenda-heading" title="Today" count={query.data?.length}>
        {query.isError && query.data && <RefreshFailedNote onRetry={() => void query.refetch()} />}
      </SectionHeader>
      {query.isPending ? (
        <ListSkeleton rows={4} />
      ) : query.isError && !query.data ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.length === 0 ? (
        <EmptyState
          title="Nothing on your plate today."
          description="Tasks scheduled for today, or due by today, show up here."
        />
      ) : (
        // Backend order (most time-critical first) is preserved as-is.
        <ul className="divide-y divide-border/70 border-y border-border/70">
          {query.data.map((task) => (
            <TaskRow key={task.id} task={task} />
          ))}
        </ul>
      )}
    </section>
  )
}

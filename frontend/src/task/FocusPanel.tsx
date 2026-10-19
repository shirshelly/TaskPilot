import { useEffect, useState } from 'react'
import { Check, Loader2 } from 'lucide-react'
import { useSearchParams } from 'react-router'
import type { Task } from '@/api/types'
import { useTaskOverlays } from '@/app/taskOverlays'
import { Button } from '@/components/ui/button'
import { EmptyState, ErrorState, ListSkeleton, RefreshFailedNote, SectionHeader } from '@/components/states'
import { Skeleton } from '@/components/ui/skeleton'
import { MINUTES_PARAM, minutesPhrase, parseAvailableMinutes } from '@/lib/minutes'
import { cn } from '@/lib/utils'
import { useIsTaskBusy } from '@/queries/mutations'
import { useCompletionFlow } from './useCompletionFlow'
import { useRecommendations } from '@/queries/queries'
import { TaskMeta } from './TaskMeta'
import { TaskRow } from './TaskRow'
import { TimeFilter } from './TimeFilter'

const INITIAL_ALTERNATIVES = 4

/**
 * "What should I focus on right now?" The backend ranks; this panel only presents the result:
 * the first task is featured, the rest follow in the same order. No scores or reasons are shown
 * because the API exposes none.
 */
export function FocusPanel({ className }: { className?: string }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawMinutes = searchParams.get(MINUTES_PARAM)
  const minutes = parseAvailableMinutes(rawMinutes)

  const setMinutes = (next: number | null) =>
    setSearchParams(
      (previous) => {
        const params = new URLSearchParams(previous)
        if (next === null) params.delete(MINUTES_PARAM)
        else params.set(MINUTES_PARAM, String(next))
        return params
      },
      { replace: true },
    )

  // A hand-edited or stale link (?minutes=abc) means "Any time"; tidy the URL to match.
  useEffect(() => {
    if (rawMinutes !== null && minutes === null) setMinutes(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rawMinutes, minutes])

  const query = useRecommendations(minutes)

  return (
    <section aria-labelledby="focus-heading" className={className}>
      <SectionHeader id="focus-heading" title="Focus" />
      <TimeFilter minutes={minutes} onChange={setMinutes} />
      {minutes !== null && (
        <p className="mt-2 text-xs text-muted-foreground">
          Shows tasks whose minimum session — or estimated duration, if there's none — fits this time.
        </p>
      )}

      <div className="mt-5">
        {query.isPending ? (
          <FocusSkeleton />
        ) : query.isError && !query.data ? (
          <ErrorState error={query.error} onRetry={() => void query.refetch()} />
        ) : (
          <>
            {query.isError && <RefreshFailedNote onRetry={() => void query.refetch()} />}
            <div
              aria-busy={query.isPlaceholderData}
              className={cn('transition-opacity', query.isPlaceholderData && 'opacity-50')}
            >
              {/* Keyed by the filter so "Show more" collapses again when the filter changes. */}
              <Recommendations
                key={minutes ?? 'any'}
                tasks={query.data ?? []}
                minutes={minutes}
                onShowAll={() => setMinutes(null)}
              />
            </div>
          </>
        )}
      </div>
    </section>
  )
}

function Recommendations({ tasks, minutes, onShowAll }: { tasks: Task[]; minutes: number | null; onShowAll: () => void }) {
  const [expanded, setExpanded] = useState(false)

  if (tasks.length === 0) {
    return minutes === null ? (
      <EmptyState title="Nothing actionable right now." description="Active tasks will be suggested here." />
    ) : (
      <EmptyState
        title={`Nothing fits in ${minutesPhrase(minutes)}.`}
        action={
          <Button variant="outline" size="sm" onClick={onShowAll}>
            Show all (Any time)
          </Button>
        }
      />
    )
  }

  const [featured, ...alternatives] = tasks
  const visible = expanded ? alternatives : alternatives.slice(0, INITIAL_ALTERNATIVES)
  const hidden = alternatives.length - visible.length

  return (
    <>
      <FeaturedTask task={featured} />
      {alternatives.length > 0 && (
        <div className="mt-6">
          <p className="text-xs text-muted-foreground">Then</p>
          <ul aria-label="Other suggestions" className="divide-y divide-border/70">
            {visible.map((task) => (
              <TaskRow key={task.id} task={task} />
            ))}
          </ul>
          {hidden > 0 && (
            <Button variant="ghost" size="sm" className="-ml-2.5 text-muted-foreground" onClick={() => setExpanded(true)}>
              Show {hidden} more
            </Button>
          )}
        </div>
      )}
    </>
  )
}

function FeaturedTask({ task }: { task: Task }) {
  const overlays = useTaskOverlays()
  const busy = useIsTaskBusy(task.id)
  const completion = useCompletionFlow(task)
  return (
    <article
      aria-labelledby="featured-title"
      aria-busy={busy}
      className={cn(
        'relative overflow-hidden rounded-2xl border border-border bg-card p-5 shadow-[0_1px_2px_rgb(0_0_0/0.04),0_12px_32px_-16px_rgb(30_40_40/0.25)] transition-opacity sm:p-6',
        busy && 'opacity-60',
      )}
    >
      <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-brand/80" />
      <p className="flex items-center gap-2 text-xs font-medium tracking-[0.14em] text-brand uppercase">Start with</p>
      <h3 id="featured-title" className="mt-2 font-serif text-[28px] leading-[1.15] break-words">
        {task.title}
      </h3>
      {task.description && <p className="mt-2 line-clamp-3 text-sm text-muted-foreground">{task.description}</p>}
      <TaskMeta task={task} className="mt-4" />
      <div className="mt-5 flex flex-wrap gap-2">
        <Button
          onClick={completion.start}
          disabled={busy}
          aria-busy={completion.isPending}
          aria-haspopup={completion.asksFirst ? 'dialog' : undefined}
        >
          {completion.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <Check aria-hidden="true" />}
          Mark done
        </Button>
        <Button variant="ghost" onClick={() => overlays.openEdit(task)} disabled={busy}>
          Open
        </Button>
      </div>
    </article>
  )
}

function FocusSkeleton() {
  return (
    <div aria-hidden="true">
      <div className="rounded-2xl border border-border bg-card p-6">
        <Skeleton className="h-3 w-20" />
        <Skeleton className="mt-4 h-7 w-3/4" />
        <Skeleton className="mt-4 h-3 w-1/2" />
      </div>
      <ListSkeleton rows={2} className="mt-6" />
    </div>
  )
}

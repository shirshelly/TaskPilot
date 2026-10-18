import type { ReactNode } from 'react'
import { AlertCircle, RotateCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/api/errors'
import { cn } from '@/lib/utils'

/** Placeholder rows shaped like TaskRow, for the first load only. */
export function ListSkeleton({ rows = 3, className }: { rows?: number; className?: string }) {
  return (
    <ul aria-hidden="true" className={cn('divide-y divide-border/70', className)}>
      {Array.from({ length: rows }, (_, index) => (
        <li key={index} className="flex gap-3 py-4">
          <Skeleton className="mt-0.5 size-[18px] rounded-full" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4" style={{ width: `${70 - index * 12}%` }} />
            <Skeleton className="h-3 w-1/3" />
          </div>
        </li>
      ))}
    </ul>
  )
}

export function EmptyState({
  title,
  description,
  action,
  className,
}: {
  title: string
  description?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('rounded-xl border border-dashed border-border px-5 py-8 text-center', className)}>
      <p className="font-medium">{title}</p>
      {description && <p className="mx-auto mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

/** Copy for a failed read; chosen by error code, never by parsing server messages. */
function describeLoadError(error: unknown): string {
  if (error instanceof ApiError && error.code === 'NETWORK_ERROR') {
    return "Can't reach the TaskPilot server. Check that the backend is running."
  }
  return 'Something went wrong while loading.'
}

/** Shown in place of a list that failed to load and has nothing to show. */
export function ErrorState({ error, onRetry, className }: { error: unknown; onRetry: () => void; className?: string }) {
  return (
    <div role="alert" className={cn('rounded-xl border border-border bg-card px-5 py-6 text-center', className)}>
      <AlertCircle className="mx-auto size-5 text-muted-foreground" aria-hidden="true" />
      <p className="mt-2 text-sm">{describeLoadError(error)}</p>
      <Button variant="outline" size="sm" className="mt-4" onClick={onRetry}>
        <RotateCw aria-hidden="true" />
        Try again
      </Button>
    </div>
  )
}

/** A background refresh failed but older data is still on screen. */
export function RefreshFailedNote({ onRetry }: { onRetry: () => void }) {
  return (
    <p role="status" className="flex items-center gap-1.5 text-xs text-muted-foreground">
      <AlertCircle className="size-3.5" aria-hidden="true" />
      Couldn't refresh.
      <button type="button" onClick={onRetry} className="font-medium text-foreground underline-offset-4 hover:underline">
        Retry
      </button>
    </p>
  )
}

export function SectionHeader({
  id,
  title,
  count,
  children,
}: {
  id: string
  title: string
  count?: number
  children?: ReactNode
}) {
  return (
    <div className="mb-3 flex min-h-7 items-center justify-between gap-3">
      <h2 id={id} className="text-xs font-medium tracking-[0.14em] text-muted-foreground uppercase">
        {title}
        {count !== undefined && count > 0 && <span className="ml-2 tabular-nums text-foreground/60">{count}</span>}
      </h2>
      {children}
    </div>
  )
}

/** Inline message inside a form or sheet (errors, conflicts, notices). */
export function InlineAlert({
  tone = 'error',
  title,
  children,
  action,
}: {
  tone?: 'error' | 'warning' | 'info'
  title: string
  children?: ReactNode
  action?: ReactNode
}) {
  return (
    <div
      role={tone === 'info' ? 'status' : 'alert'}
      className={cn(
        'rounded-lg border px-3.5 py-3 text-sm',
        tone === 'error' && 'border-destructive/25 bg-destructive/5',
        tone === 'warning' && 'border-amber-300/70 bg-amber-50',
        tone === 'info' && 'border-border bg-muted/60',
      )}
    >
      <p className="font-medium">{title}</p>
      {children && <div className="mt-1 text-muted-foreground">{children}</div>}
      {action && <div className="mt-3 flex flex-wrap gap-2">{action}</div>}
    </div>
  )
}

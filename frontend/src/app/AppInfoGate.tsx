import type { ReactNode } from 'react'
import { ErrorState, ListSkeleton } from '@/components/states'
import { Skeleton } from '@/components/ui/skeleton'
import { useAppInfoQuery } from '@/queries/queries'
import { AppTimeProvider } from './appTime'

/**
 * Every date the UI shows or builds depends on the backend's application zone, so pages render
 * only once GET /app-info has answered. It is fetched once per session.
 */
export function AppInfoGate({ children }: { children: ReactNode }) {
  const query = useAppInfoQuery()

  if (query.isPending) {
    return (
      <div aria-busy="true" aria-label="Loading" className="pt-12">
        <Skeleton className="h-12 w-56" />
        <Skeleton className="mt-3 h-4 w-32" />
        <ListSkeleton rows={4} className="mt-10 max-w-2xl" />
      </div>
    )
  }
  if (query.isError) {
    return (
      <div className="mx-auto max-w-md pt-24">
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      </div>
    )
  }
  return <AppTimeProvider zone={query.data.zoneId}>{children}</AppTimeProvider>
}

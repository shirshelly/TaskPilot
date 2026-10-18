import type { ReactNode } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import { Plus } from 'lucide-react'
import { ErrorState, ListSkeleton } from '@/components/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { useAppInfoQuery } from '@/queries/queries'
import { AppTimeProvider } from './appTime'
import { TaskOverlaysProvider, useTaskOverlays } from './taskOverlays'

/**
 * Every date the UI shows or builds depends on the backend's application zone, so pages (and
 * the task dialogs) render only once GET /app-info has answered. It is fetched once per session.
 */
export function AppShell() {
  const appInfo = useAppInfoQuery()

  if (appInfo.isPending) {
    return (
      <Frame>
        <div aria-busy="true" aria-label="Loading" className="pt-12">
          <Skeleton className="h-12 w-56" />
          <Skeleton className="mt-3 h-4 w-32" />
          <ListSkeleton rows={4} className="mt-10 max-w-2xl" />
        </div>
      </Frame>
    )
  }
  if (appInfo.isError) {
    return (
      <Frame>
        <div className="mx-auto max-w-md pt-24">
          <ErrorState error={appInfo.error} onRetry={() => void appInfo.refetch()} />
        </div>
      </Frame>
    )
  }
  return (
    <AppTimeProvider zone={appInfo.data.zoneId}>
      <TaskOverlaysProvider>
        <Frame action={<NewTaskButton />}>
          <Outlet />
        </Frame>
        <NewTaskFab />
      </TaskOverlaysProvider>
    </AppTimeProvider>
  )
}

function Frame({ action, children }: { action?: ReactNode; children: ReactNode }) {
  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-30 border-b border-border/60 bg-background/85 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-[1120px] items-center gap-6 px-4 sm:px-6 lg:px-8">
          <Link
            to="/"
            className="flex items-center gap-2 rounded-md font-semibold tracking-tight outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
          >
            <Logo />
            TaskPilot
          </Link>
          <nav aria-label="Main" className="flex items-center gap-1 text-sm">
            <ShellLink to="/" end>
              Today
            </ShellLink>
            <ShellLink to="/tasks">Tasks</ShellLink>
          </nav>
          {action && <div className="ml-auto hidden sm:block">{action}</div>}
        </div>
      </header>
      <main className="mx-auto max-w-[1120px] px-4 pb-28 sm:px-6 lg:px-8">{children}</main>
    </div>
  )
}

function NewTaskButton() {
  const { openCreate } = useTaskOverlays()
  return (
    <Button onClick={openCreate}>
      <Plus aria-hidden="true" />
      New task
    </Button>
  )
}

/** On phones the header action becomes a thumb-reachable floating button. */
function NewTaskFab() {
  const { openCreate } = useTaskOverlays()
  return (
    <Button
      onClick={openCreate}
      aria-label="New task"
      className="fixed right-4 bottom-[max(1rem,env(safe-area-inset-bottom))] z-30 size-14 rounded-full shadow-lg sm:hidden"
    >
      <Plus className="size-6" aria-hidden="true" />
    </Button>
  )
}

function ShellLink({ to, end, children }: { to: string; end?: boolean; children: string }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          'rounded-md px-2.5 py-1.5 font-medium text-muted-foreground transition-colors outline-none hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50',
          isActive && 'bg-muted text-foreground',
        )
      }
    >
      {children}
    </NavLink>
  )
}

function Logo() {
  return (
    <svg viewBox="0 0 24 24" className="size-6" aria-hidden="true">
      <rect width="24" height="24" rx="7" className="fill-brand" />
      <circle cx="12" cy="12" r="5.25" fill="none" stroke="white" strokeWidth="1.75" opacity="0.55" />
      <circle cx="12" cy="12" r="2" fill="white" />
    </svg>
  )
}

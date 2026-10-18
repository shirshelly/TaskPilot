import { Link, NavLink, Outlet } from 'react-router'
import { cn } from '@/lib/utils'
import { AppInfoGate } from './AppInfoGate'

export function AppShell() {
  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-30 border-b border-border/60 bg-background/85 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-[1120px] items-center gap-6 px-4 sm:px-6 lg:px-8">
          <Link to="/" className="flex items-center gap-2 rounded-md font-semibold tracking-tight outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50">
            <Logo />
            TaskPilot
          </Link>
          <nav aria-label="Main" className="flex items-center gap-1 text-sm">
            <ShellLink to="/" end>
              Today
            </ShellLink>
            <ShellLink to="/tasks">Tasks</ShellLink>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-[1120px] px-4 pb-24 sm:px-6 lg:px-8">
        <AppInfoGate>
          <Outlet />
        </AppInfoGate>
      </main>
    </div>
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

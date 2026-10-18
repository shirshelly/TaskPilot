import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/queries/queries'
import { appToday, type IsoDate } from '@/lib/time'

interface AppTime {
  /** The backend's application zone (GET /app-info). */
  zone: string
  /** Today's date in that zone; updates when the zone's date changes. */
  today: IsoDate
}

const AppTimeContext = createContext<AppTime | null>(null)

const CLOCK_TICK_MS = 60_000

/**
 * Provides the app zone and "today", and refreshes all task data when the date rolls over in
 * the app zone: /agenda/today, overdue flags and ranking all depend on the date. The check runs
 * every minute and whenever the tab becomes visible again (e.g. a laptop waking up).
 */
export function AppTimeProvider({ zone, children }: { zone: string; children: ReactNode }) {
  const queryClient = useQueryClient()
  const [today, setToday] = useState(() => appToday(zone))
  const previousToday = useRef(today)

  useEffect(() => {
    const update = () => setToday(appToday(zone))
    update()
    const interval = window.setInterval(update, CLOCK_TICK_MS)
    document.addEventListener('visibilitychange', update)
    return () => {
      window.clearInterval(interval)
      document.removeEventListener('visibilitychange', update)
    }
  }, [zone])

  useEffect(() => {
    if (previousToday.current !== today) {
      previousToday.current = today
      void queryClient.invalidateQueries({ queryKey: queryKeys.root })
    }
  }, [today, queryClient])

  return <AppTimeContext.Provider value={{ zone, today }}>{children}</AppTimeContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAppTime(): AppTime {
  const value = useContext(AppTimeContext)
  if (!value) throw new Error('useAppTime must be used inside <AppTimeProvider>')
  return value
}

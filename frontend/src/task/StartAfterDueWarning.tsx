import { AlertTriangle } from 'lucide-react'

/**
 * Non-blocking hint (§7): starting after the due date is allowed by the backend, but usually a
 * mistake. Compares two dates the user is entering; it decides nothing.
 */
export function StartAfterDueWarning({ startDate, deadline }: { startDate: string; deadline: string }) {
  if (!startDate || !deadline || startDate <= deadline) return null
  return (
    <p role="status" className="flex items-center gap-1.5 text-xs text-amber-700">
      <AlertTriangle className="size-3.5 shrink-0" aria-hidden="true" />
      Starts after its due date.
    </p>
  )
}

import { AlertTriangle } from 'lucide-react'
import { sessionLongerThanTotal, type TaskDraft } from '@/lib/patch'

/** Non-blocking: the backend accepts it, but it's usually a typo (e.g. hours vs minutes). */
export function SessionLongerWarning({ draft }: { draft: TaskDraft }) {
  if (!sessionLongerThanTotal(draft)) return null
  return (
    <p role="status" className="flex items-start gap-1.5 text-xs text-amber-700">
      <AlertTriangle className="mt-px size-3.5 shrink-0" aria-hidden="true" />
      Minimum session is longer than the estimated total duration. Is that intentional?
    </p>
  )
}

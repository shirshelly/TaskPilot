import { AlertTriangle } from 'lucide-react'
import type { Task } from '@/api/types'
import { useAppTime } from '@/app/appTime'
import { formatCalendarDate, formatDuration, formatSchedule } from '@/lib/format'
import {
  buildPatch,
  changedFields,
  FIELD_LABELS,
  valuesFromDraft,
  valuesFromTask,
  type EditableField,
  type EditableValues,
  type TaskDraft,
} from '@/lib/patch'

const STATUS_LABELS: Record<Task['status'], string> = { ACTIVE: 'Active', SCHEDULED: 'Scheduled', COMPLETED: 'Completed' }

/**
 * What changed between the version the user started editing (`base`) and the server's latest,
 * next to the user's own edits. Display only — the decision stays with the user.
 */
export function ConflictReview({ base, latest, draft }: { base: Task; latest: Task; draft: TaskDraft }) {
  const { zone, today } = useAppTime()
  const theirs = new Set(changedFields(base, latest))
  const mine = new Set(Object.keys(buildPatch(base, draft)) as EditableField[])
  const original = valuesFromTask(base)
  const server = valuesFromTask(latest)
  const yours = valuesFromDraft(draft)

  const show = (field: EditableField, values: EditableValues) => formatValue(field, values, today)
  const fields = (Object.keys(FIELD_LABELS) as EditableField[]).filter((field) => theirs.has(field) || mine.has(field))
  const scheduleLabel = (task: Task) => (task.scheduledFor ? formatSchedule(task.scheduledFor, zone, today) : 'Not scheduled')

  return (
    <dl className="divide-y divide-border rounded-lg border border-border bg-card text-sm">
      {latest.status !== base.status && (
        <Row label="Status" note="Changed elsewhere">
          {STATUS_LABELS[base.status]} → {STATUS_LABELS[latest.status]}
        </Row>
      )}
      {latest.scheduledFor !== base.scheduledFor && (
        <Row label="Start on" note="Changed elsewhere">
          {scheduleLabel(base)} → {scheduleLabel(latest)}
        </Row>
      )}
      {fields.map((field) => {
        const both = theirs.has(field) && mine.has(field)
        if (both && server[field] !== yours[field]) {
          return (
            <Row key={field} label={FIELD_LABELS[field]} warning>
              <span className="block">Was: {show(field, original)}</span>
              <span className="block">Now: {show(field, server)}</span>
              <span className="block font-medium text-foreground">Yours: {show(field, yours)}</span>
            </Row>
          )
        }
        if (theirs.has(field)) {
          return (
            <Row key={field} label={FIELD_LABELS[field]} note={both ? 'Same as your change' : 'Changed elsewhere · kept'}>
              {show(field, original)} → {show(field, server)}
            </Row>
          )
        }
        return (
          <Row key={field} label={FIELD_LABELS[field]} note="Your change">
            {show(field, original)} → {show(field, yours)}
          </Row>
        )
      })}
      {fields.length === 0 && latest.status === base.status && latest.scheduledFor === base.scheduledFor && (
        <Row label="Details">Nothing you can edit here changed — saving will keep everything else as it is.</Row>
      )}
    </dl>
  )
}

function Row({ label, note, warning, children }: { label: string; note?: string; warning?: boolean; children: React.ReactNode }) {
  return (
    <div className="px-3 py-2.5">
      <dt className="flex items-center justify-between gap-2 text-xs">
        <span className="font-medium text-foreground">{label}</span>
        {warning ? (
          <span className="inline-flex items-center gap-1 font-medium text-amber-700">
            <AlertTriangle className="size-3.5" aria-hidden="true" />
            Changed on both sides
          </span>
        ) : (
          note && <span className="text-muted-foreground">{note}</span>
        )}
      </dt>
      <dd className="mt-1 break-words text-muted-foreground">{children}</dd>
    </div>
  )
}

function formatValue(field: EditableField, values: EditableValues, today: string): string {
  switch (field) {
    case 'title':
      return `“${values.title}”`
    case 'description':
      return values.description === null ? 'none' : `“${truncate(values.description)}”`
    case 'importance':
      return String(values.importance)
    case 'estimatedDurationMinutes':
      return values.estimatedDurationMinutes === null ? 'no estimate' : formatDuration(values.estimatedDurationMinutes)
    case 'minSessionMinutes':
      return values.minSessionMinutes === null ? 'none' : formatDuration(values.minSessionMinutes)
    case 'deadline':
      return values.deadline === null ? 'none' : formatCalendarDate(values.deadline, today)
  }
}

function truncate(text: string): string {
  return text.length > 60 ? `${text.slice(0, 57)}…` : text
}

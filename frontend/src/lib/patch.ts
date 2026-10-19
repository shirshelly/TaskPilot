import type { CreateTaskRequest, Task, TaskPatch } from '@/api/types'
import { durationDraftFromMinutes, EMPTY_DURATION, minutesOf, parseDuration, type DurationDraft } from './duration'
import { startOfDayIso, zonedDateTimeIso, type IsoDate } from './time'

/**
 * Form state for the editable (non-lifecycle) fields, as the inputs hold it: strings for text
 * and number inputs, '' for "empty". Converted to API values only at the edges.
 */
export interface TaskDraft {
  title: string
  description: string
  importance: number
  /** Total estimate as typed, in minutes or hours; empty text means no estimate. */
  duration: DurationDraft
  /** Minimum session as typed, in minutes or hours; empty text means none. */
  minSession: DurationDraft
  /** 'YYYY-MM-DD' or ''. */
  deadline: string
}

export const EDITABLE_FIELDS = [
  'title',
  'description',
  'importance',
  'estimatedDurationMinutes',
  'minSessionMinutes',
  'deadline',
] as const
export type EditableField = (typeof EDITABLE_FIELDS)[number]

/** The editable fields in API shape (null = empty). */
export interface EditableValues {
  title: string
  description: string | null
  importance: number
  estimatedDurationMinutes: number | null
  minSessionMinutes: number | null
  deadline: string | null
}

export const FIELD_LABELS: Record<EditableField, string> = {
  title: 'Title',
  description: 'Description',
  importance: 'Importance',
  estimatedDurationMinutes: 'Estimated duration',
  minSessionMinutes: 'Minimum session',
  deadline: 'Due by',
}

export function draftFromTask(task: Task): TaskDraft {
  return {
    title: task.title,
    description: task.description ?? '',
    importance: task.importance,
    duration: durationDraftFromMinutes(task.estimatedDurationMinutes),
    minSession: durationDraftFromMinutes(task.minSessionMinutes),
    deadline: task.deadline ?? '',
  }
}

export function valuesFromTask(task: Task): EditableValues {
  return {
    title: task.title,
    description: task.description,
    importance: task.importance,
    estimatedDurationMinutes: task.estimatedDurationMinutes,
    minSessionMinutes: task.minSessionMinutes,
    deadline: task.deadline,
  }
}

/**
 * Draft → API values. Title is trimmed; a blank description, duration or deadline means "none".
 * Only call on a draft that passed validateDraft. Durations become whole minutes whatever unit
 * they were typed in, so changing only the display unit never shows up as a change.
 */
export function valuesFromDraft(draft: TaskDraft): EditableValues {
  const description = draft.description.trim() === '' ? null : draft.description
  return {
    title: draft.title.trim(),
    description,
    importance: draft.importance,
    estimatedDurationMinutes: minutesOf(draft.duration),
    minSessionMinutes: minutesOf(draft.minSession),
    deadline: draft.deadline === '' ? null : draft.deadline,
  }
}

export type DraftErrors = Partial<Record<'title' | 'duration' | 'minSession', string>>

/** Fast feedback for obviously invalid input. The backend still validates everything. */
export function validateDraft(draft: TaskDraft): DraftErrors {
  const errors: DraftErrors = {}
  if (draft.title.trim() === '') errors.title = 'Give the task a title.'
  const duration = parseDuration(draft.duration)
  if (!duration.ok) errors.duration = duration.error
  const minSession = parseDuration(draft.minSession)
  if (!minSession.ok) errors.minSession = minSession.error
  return errors
}

/**
 * Non-blocking consistency check (compares minutes, not typed text): a minimum session longer
 * than the whole task is allowed by the backend but is probably a typo.
 */
export function sessionLongerThanTotal(draft: TaskDraft): boolean {
  const total = minutesOf(draft.duration)
  const session = minutesOf(draft.minSession)
  return total !== null && session !== null && session > total
}

/**
 * The PATCH body for editing `base` into `draft`: changed fields only, a cleared nullable field
 * as explicit null, and nothing at all for unchanged fields. `{}` means there is nothing to send.
 */
export function buildPatch(base: Task, draft: TaskDraft): TaskPatch {
  const before = valuesFromTask(base)
  const after = valuesFromDraft(draft)
  const patch: Record<string, unknown> = {}
  for (const field of EDITABLE_FIELDS) {
    if (before[field] !== after[field]) patch[field] = after[field]
  }
  return patch as TaskPatch
}

/** Editable fields whose values differ between two versions of a task. */
export function changedFields(from: Task, to: Task): EditableField[] {
  const a = valuesFromTask(from)
  const b = valuesFromTask(to)
  return EDITABLE_FIELDS.filter((field) => a[field] !== b[field])
}

export interface Rebase {
  /** Latest server values, with only the fields the user changed (vs. `base`) re-applied. */
  draft: TaskDraft
  /** Fields both sides changed to different values — overwriting them needs confirmation. */
  overlaps: EditableField[]
}

/**
 * Moves an edit onto a newer server version without touching fields the user didn't change.
 * Building the PATCH against `latest` afterwards therefore sends only the user's own changes
 * (minus any the server already holds) and can never revert a server-only change.
 */
export function rebaseDraft(base: Task, draft: TaskDraft, latest: Task): Rebase {
  const mine = Object.keys(buildPatch(base, draft)) as EditableField[]
  const theirs = new Set(changedFields(base, latest))
  const latestValues = valuesFromTask(latest)
  const myValues = valuesFromDraft(draft)

  const rebased = draftFromTask(latest)
  for (const field of mine) {
    if (field === 'title') rebased.title = draft.title
    else if (field === 'description') rebased.description = draft.description
    else if (field === 'importance') rebased.importance = draft.importance
    else if (field === 'estimatedDurationMinutes') rebased.duration = draft.duration
    else if (field === 'minSessionMinutes') rebased.minSession = draft.minSession
    else rebased.deadline = draft.deadline
  }
  const overlaps = mine.filter((field) => theirs.has(field) && latestValues[field] !== myValues[field])
  return { draft: rebased, overlaps }
}

/** Start on as the form holds it: a date, optionally with a wall-clock time. */
export interface StartOnInput {
  date: IsoDate | ''
  time: string
}

/**
 * scheduledFor for a Start on choice, in the application zone. Date-only is represented as the
 * first instant of that day; activation only looks at the date (§9), so this is purely a
 * representation convention.
 */
export function scheduledForFromInput(startOn: StartOnInput, zone: string): string | undefined {
  if (startOn.date === '') return undefined
  return startOn.time === '' ? startOfDayIso(startOn.date, zone) : zonedDateTimeIso(startOn.date, startOn.time, zone)
}

/** POST /tasks body: empty optional fields are omitted; status is never sent. */
export function buildCreateRequest(draft: TaskDraft, startOn: StartOnInput, zone: string): CreateTaskRequest {
  const values = valuesFromDraft(draft)
  const request: CreateTaskRequest = { title: values.title, importance: values.importance }
  if (values.description !== null) request.description = values.description
  if (values.estimatedDurationMinutes !== null) request.estimatedDurationMinutes = values.estimatedDurationMinutes
  if (values.minSessionMinutes !== null) request.minSessionMinutes = values.minSessionMinutes
  if (values.deadline !== null) request.deadline = values.deadline
  const scheduledFor = scheduledForFromInput(startOn, zone)
  if (scheduledFor !== undefined) request.scheduledFor = scheduledFor
  return request
}

export const EMPTY_DRAFT: TaskDraft = {
  title: '',
  description: '',
  importance: 3,
  duration: EMPTY_DURATION,
  minSession: EMPTY_DURATION,
  deadline: '',
}

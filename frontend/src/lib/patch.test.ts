import { describe, expect, it } from 'vitest'
import type { Task } from '@/api/types'
import { task } from '@/test/render'
import type { DurationDraft } from './duration'
import {
  buildCreateRequest,
  buildPatch,
  changedFields,
  draftFromTask,
  EMPTY_DRAFT,
  rebaseDraft,
  sessionLongerThanTotal,
  validateDraft,
} from './patch'

const m = (text: string): DurationDraft => ({ text, unit: 'min' })
const hours = (text: string): DurationDraft => ({ text, unit: 'h' })

const base: Task = task({
  title: 'Write report',
  description: 'Q4 numbers',
  importance: 3,
  estimatedDurationMinutes: 45,
  deadline: '2026-10-23',
  version: 3,
})

describe('buildPatch', () => {
  it('is empty when nothing changed', () => {
    expect(buildPatch(base, draftFromTask(base))).toEqual({})
  })

  it('omits unchanged fields and sends only the changed ones', () => {
    const draft = { ...draftFromTask(base), title: 'Write the report' }
    expect(buildPatch(base, draft)).toEqual({ title: 'Write the report' })
  })

  it('sends an explicit null for each cleared nullable field', () => {
    const draft = { ...draftFromTask(base), description: '', duration: m(''), deadline: '' }
    expect(buildPatch(base, draft)).toEqual({ description: null, estimatedDurationMinutes: null, deadline: null })
  })

  it('treats whitespace as empty and trims the title, so cosmetic edits are not changes', () => {
    const noDescription = task({ description: null, title: 'Same' })
    expect(buildPatch(noDescription, { ...draftFromTask(noDescription), description: '   ', title: '  Same ' })).toEqual({})
  })

  it('converts typed values to API types', () => {
    const draft = { ...draftFromTask(base), duration: hours('1.5'), importance: 5, deadline: '2026-11-01' }
    expect(buildPatch(base, draft)).toEqual({ estimatedDurationMinutes: 90, importance: 5, deadline: '2026-11-01' })
  })

  it('treats the minimum session like the other nullable minutes field', () => {
    const ongoing = task({ estimatedDurationMinutes: 600, minSessionMinutes: 10 })
    expect(buildPatch(ongoing, { ...draftFromTask(ongoing), minSession: m('15') })).toEqual({ minSessionMinutes: 15 })
    expect(buildPatch(ongoing, { ...draftFromTask(ongoing), minSession: m('') })).toEqual({ minSessionMinutes: null })
    expect(buildPatch(ongoing, { ...draftFromTask(ongoing), title: 'x' })).not.toHaveProperty('minSessionMinutes')
  })

  it('sends nothing when only the display unit changes', () => {
    const report = task({ estimatedDurationMinutes: 90, minSessionMinutes: 30 })
    // 90 min opens as 1.5 h; showing it in minutes (or hours again) is not a change.
    expect(draftFromTask(report).duration).toEqual(hours('1.5'))
    expect(buildPatch(report, { ...draftFromTask(report), duration: m('90'), minSession: hours('0.5') })).toEqual({})
  })

  it('sends whole minutes for a duration typed in hours', () => {
    expect(buildPatch(base, { ...draftFromTask(base), duration: hours('4.5') })).toEqual({ estimatedDurationMinutes: 270 })
  })

  it('never contains lifecycle fields', () => {
    const draft = { title: 'x', description: '', duration: m('1'), minSession: m('5'), importance: 1, deadline: '' }
    expect(Object.keys(buildPatch(base, draft)).sort()).toEqual(
      ['deadline', 'description', 'estimatedDurationMinutes', 'importance', 'minSessionMinutes', 'title'].sort(),
    )
  })
})

describe('conflict rebase', () => {
  // The server changed description and importance; the user changed title and importance.
  const latest: Task = { ...base, description: 'Q4 numbers + charts', importance: 5, version: 4 }
  const mine = { ...draftFromTask(base), title: 'Write the report', importance: 4 }

  it('lists the fields the server changed', () => {
    expect(changedFields(base, latest)).toEqual(['description', 'importance'])
  })

  it('keeps server-only changes and re-applies only my fields', () => {
    const { draft } = rebaseDraft(base, mine, latest)
    expect(draft.description).toBe('Q4 numbers + charts')
    expect(draft.title).toBe('Write the report')
    expect(draft.importance).toBe(4)
    // The resulting PATCH can't revert the server's description.
    expect(buildPatch(latest, draft)).toEqual({ title: 'Write the report', importance: 4 })
  })

  it('reports a field both sides changed differently as an overlap', () => {
    expect(rebaseDraft(base, mine, latest).overlaps).toEqual(['importance'])
  })

  it('does not treat the same change on both sides as a conflict, and does not resend it', () => {
    const sameChange = { ...draftFromTask(base), importance: 5 }
    const { draft, overlaps } = rebaseDraft(base, sameChange, latest)
    expect(overlaps).toEqual([])
    expect(buildPatch(latest, draft)).toEqual({})
  })

  it('re-applies a changed minimum session without touching the server-changed total', () => {
    const ongoing = task({ estimatedDurationMinutes: 600, minSessionMinutes: 10, version: 1 })
    const latestOngoing = { ...ongoing, estimatedDurationMinutes: 900, version: 2 }
    const { draft, overlaps } = rebaseDraft(ongoing, { ...draftFromTask(ongoing), minSession: m('20') }, latestOngoing)
    expect(overlaps).toEqual([])
    expect(buildPatch(latestOngoing, draft)).toEqual({ minSessionMinutes: 20 })
  })

  it('treats clearing a field the server also changed as an overlap', () => {
    const cleared = { ...draftFromTask(base), description: '' }
    const { overlaps, draft } = rebaseDraft(base, cleared, latest)
    expect(overlaps).toEqual(['description'])
    expect(buildPatch(latest, draft)).toEqual({ description: null })
  })
})

describe('validateDraft', () => {
  it('requires a title and a positive whole duration', () => {
    expect(validateDraft({ ...EMPTY_DRAFT, title: ' ' })).toHaveProperty('title')
    expect(validateDraft({ ...EMPTY_DRAFT, title: 'x', duration: m('0') })).toHaveProperty('duration')
    expect(validateDraft({ ...EMPTY_DRAFT, title: 'x', duration: m('1.5') })).toHaveProperty('duration')
    expect(validateDraft({ ...EMPTY_DRAFT, title: 'x', duration: m('') })).toEqual({})
    expect(validateDraft({ ...EMPTY_DRAFT, title: 'x', minSession: m('0') })).toHaveProperty('minSession')
    expect(validateDraft({ ...EMPTY_DRAFT, title: 'x', minSession: m('-5') })).toHaveProperty('minSession')
    expect(validateDraft({ ...EMPTY_DRAFT, title: 'x', minSession: m('2.5') })).toHaveProperty('minSession')
    expect(validateDraft({ ...EMPTY_DRAFT, title: 'x', minSession: m('10') })).toEqual({})
  })
})

describe('sessionLongerThanTotal', () => {
  it('compares minutes, not the typed text', () => {
    const draft = { ...EMPTY_DRAFT, title: 'x' }
    expect(sessionLongerThanTotal({ ...draft, duration: hours('1'), minSession: m('90') })).toBe(true)
    // "2" > "90" as text, but 2 h = 120 min is longer than 90 min.
    expect(sessionLongerThanTotal({ ...draft, duration: hours('2'), minSession: m('90') })).toBe(false)
    expect(sessionLongerThanTotal({ ...draft, duration: m(''), minSession: m('90') })).toBe(false)
  })
})

describe('buildCreateRequest', () => {
  const zone = 'Asia/Jerusalem'

  it('sends only the title and importance for a quick task', () => {
    expect(buildCreateRequest({ ...EMPTY_DRAFT, title: ' Buy milk ' }, { date: '', time: '' }, zone)).toEqual({
      title: 'Buy milk',
      importance: 3,
    })
  })

  it('represents a date-only Start on as the start of that day in the app zone', () => {
    const request = buildCreateRequest({ ...EMPTY_DRAFT, title: 'x' }, { date: '2026-10-26', time: '' }, zone)
    expect(request.scheduledFor).toBe('2026-10-26T00:00:00.000+02:00')
  })

  it('includes every filled optional field, with the deadline as a plain date', () => {
    const draft = { title: 'x', description: 'notes', importance: 5, duration: m('30'), minSession: m(''), deadline: '2026-10-30' }
    expect(buildCreateRequest(draft, { date: '2026-10-21', time: '16:30' }, zone)).toEqual({
      title: 'x',
      description: 'notes',
      importance: 5,
      estimatedDurationMinutes: 30,
      deadline: '2026-10-30',
      scheduledFor: '2026-10-21T16:30:00.000+03:00',
    })
  })
})

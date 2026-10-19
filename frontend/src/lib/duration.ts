/**
 * Duration entry in minutes or hours. The API only ever sees positive whole minutes; units are a
 * frontend convenience. Hours are converted exactly with integer arithmetic (1.25 h = 125 × 60 /
 * 100 = 75 min), never with floating point, and anything that isn't a whole number of minutes is
 * rejected rather than rounded.
 */

export type DurationUnit = 'min' | 'h'

/** What a duration input holds: the text as typed and the unit it's typed in. */
export interface DurationDraft {
  text: string
  unit: DurationUnit
}

export const EMPTY_DURATION: DurationDraft = { text: '', unit: 'min' }

const MAX_MINUTES = 999_999
const MAX_DECIMALS = 4

export type DurationParse =
  | { ok: true; minutes: number | null } // null = left empty
  | { ok: false; error: string }

export function parseDuration(draft: DurationDraft): DurationParse {
  const text = draft.text.trim()
  if (text === '') return { ok: true, minutes: null }

  const match = /^(\d+)(?:[.,](\d+))?$/.exec(text)
  if (!match) return { ok: false, error: draft.unit === 'h' ? 'Use a number of hours, like 1.5.' : 'Use a whole number of minutes.' }

  const [, whole, fraction = ''] = match
  if (draft.unit === 'min' && fraction !== '') return { ok: false, error: 'Use a whole number of minutes.' }
  if (fraction.length > MAX_DECIMALS) return { ok: false, error: 'Use at most 4 decimal places.' }

  // value = digits / 10^decimals; minutes = value × 60 must be a whole number.
  const digits = Number(whole + fraction)
  const scale = 10 ** fraction.length
  const perUnit = draft.unit === 'h' ? 60 : 1
  if ((digits * perUnit) % scale !== 0) {
    return { ok: false, error: `${text} h isn't a whole number of minutes. Try minutes instead.` }
  }
  const minutes = (digits * perUnit) / scale
  if (minutes < 1) return { ok: false, error: 'Use at least 1 minute.' }
  if (minutes > MAX_MINUTES) return { ok: false, error: 'That is too long.' }
  return { ok: true, minutes }
}

/** Hours as an exact decimal, or null if the minutes can't be written exactly in hours. */
function exactHours(minutes: number): string | null {
  // minutes / 60 terminates iff minutes is a multiple of 3, with at most two decimals.
  if (minutes % 3 !== 0) return null
  const hundredths = (minutes * 100) / 60
  const whole = Math.floor(hundredths / 100)
  const rest = hundredths % 100
  if (rest === 0) return String(whole)
  return `${whole}.${String(rest).padStart(2, '0').replace(/0$/, '')}`
}

/** How a stored duration first appears: hours for whole quarter-hours of 1 h or more, else minutes. */
export function durationDraftFromMinutes(minutes: number | null): DurationDraft {
  if (minutes === null) return EMPTY_DURATION
  if (minutes >= 60 && minutes % 15 === 0) return { text: exactHours(minutes)!, unit: 'h' }
  return { text: String(minutes), unit: 'min' }
}

export type UnitSwitch = { ok: true; draft: DurationDraft } | { ok: false; reason: string }

/**
 * Changing the unit keeps the underlying duration: 90 min ⇄ 1.5 h. Empty or not-yet-valid text is
 * simply reinterpreted in the new unit (there's no duration to preserve yet). A value with no
 * exact representation in hours stays in minutes instead of being shown rounded.
 */
export function switchUnit(draft: DurationDraft, unit: DurationUnit): UnitSwitch {
  if (unit === draft.unit) return { ok: true, draft }
  const parsed = parseDuration(draft)
  if (!parsed.ok || parsed.minutes === null) return { ok: true, draft: { ...draft, unit } }
  if (unit === 'min') return { ok: true, draft: { text: String(parsed.minutes), unit } }
  const hours = exactHours(parsed.minutes)
  if (hours === null) return { ok: false, reason: `${parsed.minutes} min can't be written exactly in hours.` }
  return { ok: true, draft: { text: hours, unit } }
}

/** Minutes of a draft, or null when empty or invalid. */
export function minutesOf(draft: DurationDraft): number | null {
  const parsed = parseDuration(draft)
  return parsed.ok ? parsed.minutes : null
}

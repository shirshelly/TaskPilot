/** Quick choices for "I have … minutes". Absent means "Any time" (no availableMinutes sent). */
export const MINUTE_PRESETS = [15, 30, 60, 120] as const

/** URL search param holding the selection, e.g. /?minutes=30. */
export const MINUTES_PARAM = 'minutes'

// Upper bound only keeps the value a sane integer for the backend's Integer parameter; the
// backend itself has no upper limit.
const MAX_MINUTES = 999_999

/** A positive whole number of minutes, or null for anything else (absent, 0, negative, text). */
export function parseAvailableMinutes(raw: string | null | undefined): number | null {
  if (raw == null) return null
  const trimmed = raw.trim()
  if (!/^\d+$/.test(trimmed)) return null
  const minutes = Number(trimmed)
  return minutes >= 1 && minutes <= MAX_MINUTES ? minutes : null
}

export function isPreset(minutes: number | null): boolean {
  return minutes !== null && (MINUTE_PRESETS as readonly number[]).includes(minutes)
}

/** Short chip label: 15m, 30m, 1h, 2h, 90m. */
export function presetLabel(minutes: number): string {
  return minutes >= 60 && minutes % 60 === 0 ? `${minutes / 60}h` : `${minutes}m`
}

/** Sentence form: "30 minutes", "1 hour", "2 hours", "90 minutes". */
export function minutesPhrase(minutes: number): string {
  if (minutes >= 60 && minutes % 60 === 0) {
    const hours = minutes / 60
    return hours === 1 ? '1 hour' : `${hours} hours`
  }
  return minutes === 1 ? '1 minute' : `${minutes} minutes`
}

import { daysBetween, isDateOnly, zonedParts, type IsoDate } from './time'

// Display formatting only. Relative words ("Today", "Tomorrow") describe dates; they never
// decide state — overdue, Agenda membership, and ranking always come from the backend.

const utc = (options: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat('en-GB', { ...options, timeZone: 'UTC' })

const weekdayLong = utc({ weekday: 'long' })
const weekdayShort = utc({ weekday: 'short' })
const dayMonthLong = utc({ day: 'numeric', month: 'long' })
const dayMonthShort = utc({ weekday: 'short', day: 'numeric', month: 'short' })
const dayMonthYear = utc({ day: 'numeric', month: 'short', year: 'numeric' })

const asUtcDate = (date: IsoDate) => new Date(`${date}T00:00:00Z`)

export function formatDayHeading(today: IsoDate): { weekday: string; date: string } {
  const date = asUtcDate(today)
  return { weekday: weekdayLong.format(date), date: dayMonthLong.format(date) }
}

/** "Today", "Tomorrow", "Yesterday", "Thu" (within the coming week), or "Mon 2 Nov". */
export function formatCalendarDate(date: IsoDate, today: IsoDate): string {
  const offset = daysBetween(today, date)
  if (offset === 0) return 'Today'
  if (offset === 1) return 'Tomorrow'
  if (offset === -1) return 'Yesterday'
  const value = asUtcDate(date)
  if (offset > 1 && offset < 7) return weekdayShort.format(value)
  if (date.slice(0, 4) !== today.slice(0, 4)) return dayMonthYear.format(value)
  return dayMonthShort.format(value)
}

/** "Mon 26 Oct", without relative words. */
export function formatShortDate(date: IsoDate): string {
  return dayMonthShort.format(asUtcDate(date))
}

/** For use mid-sentence: "Today · 16:30" → "today · 16:30"; dates like "Mon 26 Oct" stay as they are. */
export function inSentence(label: string): string {
  return /^(Today|Tomorrow|Yesterday)\b/.test(label) ? label.charAt(0).toLowerCase() + label.slice(1) : label
}

/** Start-on label: the date, plus the time unless it's the date-only convention. */
export function formatSchedule(scheduledFor: string, zone: string, today: IsoDate): string {
  const { date, time } = zonedParts(scheduledFor, zone)
  const label = formatCalendarDate(date, today)
  return isDateOnly(scheduledFor, zone) ? label : `${label} · ${time}`
}

export function formatDeadline(deadline: IsoDate, today: IsoDate): string {
  return `Due ${inSentence(formatCalendarDate(deadline, today))}`
}

export function formatCompleted(completedAt: string, zone: string, today: IsoDate): string {
  return `Done ${inSentence(formatCalendarDate(zonedParts(completedAt, zone).date, today))}`
}

export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes}m`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}

/** Only notable importance is surfaced in rows; 1–3 stay quiet. */
export function importanceLabel(importance: number): string | null {
  if (importance >= 5) return 'Top priority'
  if (importance === 4) return 'High priority'
  return null
}

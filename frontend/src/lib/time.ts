import { TZDate, tzOffset } from '@date-fns/tz'

/**
 * Time handling in the backend's application zone (GET /app-info), never the browser's.
 * Every function takes the zone explicitly so results don't depend on where the code runs.
 */

/** A calendar date, 'YYYY-MM-DD' — the same shape as the backend's LocalDate. */
export type IsoDate = string

export interface ZonedParts {
  date: IsoDate
  /** 'HH:mm', 24-hour. */
  time: string
}

const formatters = new Map<string, Intl.DateTimeFormat>()

function partsFormatter(zone: string): Intl.DateTimeFormat {
  let formatter = formatters.get(zone)
  if (!formatter) {
    formatter = new Intl.DateTimeFormat('en-CA', {
      timeZone: zone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
    formatters.set(zone, formatter)
  }
  return formatter
}

/** The wall-clock date and time of an instant in `zone`. */
export function zonedParts(instant: string | number | Date, zone: string): ZonedParts {
  const parts = partsFormatter(zone).formatToParts(new Date(instant))
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? ''
  return {
    date: `${part('year')}-${part('month')}-${part('day')}`,
    time: `${part('hour')}:${part('minute')}`,
  }
}

/** Today's date in `zone`. */
export function appToday(zone: string, now: number = Date.now()): IsoDate {
  return zonedParts(now, zone).date
}

/**
 * The first instant of `date` in `zone`, as ISO-8601 with that zone's offset. Normally 00:00;
 * where a DST change skips midnight it is the first time that exists (e.g. 01:00).
 * This is how a date-only "Start on" is represented in scheduledFor.
 */
export function startOfDayIso(date: IsoDate, zone: string): string {
  const [year, month, day] = splitDate(date)
  return TZDate.tz(zone, year, month - 1, day).toISOString()
}

/**
 * `date` at wall-clock `time` in `zone`, as ISO-8601 with offset. A time skipped by DST moves
 * forward; a repeated (ambiguous) time resolves to its earlier occurrence.
 */
export function zonedDateTimeIso(date: IsoDate, time: string, zone: string): string {
  const [year, month, day] = splitDate(date)
  const [hours, minutes] = time.split(':').map(Number)
  // Not TZDate.tz(zone, year, ...): it reads the fields through the system zone first, so its
  // choice between the two occurrences of a repeated time depends on where the code runs.
  const wall = Date.UTC(year, month - 1, day, hours, minutes)
  const offsetBefore = tzOffset(zone, new Date(wall - 86_400_000))
  const offsetAfter = tzOffset(zone, new Date(wall + 86_400_000))
  const candidates = [wall - offsetBefore * 60_000, wall - offsetAfter * 60_000].sort((a, b) => a - b)
  const instant =
    candidates.find((t) => t + tzOffset(zone, new Date(t)) * 60_000 === wall) ?? wall - offsetBefore * 60_000
  return new TZDate(instant, zone).toISOString()
}

/** True if `instant` is exactly the start of its own day in `zone` — the date-only convention. */
export function isDateOnly(instant: string, zone: string): boolean {
  const { date } = zonedParts(instant, zone)
  return new Date(instant).getTime() === new Date(startOfDayIso(date, zone)).getTime()
}

/** Calendar arithmetic on dates (no time, no zone involved). */
export function addDays(date: IsoDate, days: number): IsoDate {
  const [year, month, day] = splitDate(date)
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10)
}

export function daysBetween(from: IsoDate, to: IsoDate): number {
  const [y1, m1, d1] = splitDate(from)
  const [y2, m2, d2] = splitDate(to)
  return Math.round((Date.UTC(y2, m2 - 1, d2) - Date.UTC(y1, m1 - 1, d1)) / 86_400_000)
}

function splitDate(date: IsoDate): [number, number, number] {
  const [year, month, day] = date.split('-').map(Number)
  return [year, month, day]
}

import { describe, expect, it } from 'vitest'
import {
  formatCalendarDate,
  formatCompleted,
  formatDayHeading,
  formatDeadline,
  formatDuration,
  formatSchedule,
  inSentence,
} from './format'

const TODAY = '2026-10-21' // a Wednesday
const JERUSALEM = 'Asia/Jerusalem'

describe('formatCalendarDate', () => {
  it('uses relative words next to today and weekdays within the coming week', () => {
    expect(formatCalendarDate('2026-10-21', TODAY)).toBe('Today')
    expect(formatCalendarDate('2026-10-22', TODAY)).toBe('Tomorrow')
    expect(formatCalendarDate('2026-10-20', TODAY)).toBe('Yesterday')
    expect(formatCalendarDate('2026-10-23', TODAY)).toBe('Fri')
  })

  it('uses a full short date further away, with the year only when it differs', () => {
    expect(formatCalendarDate('2026-10-28', TODAY)).toBe('Wed 28 Oct')
    expect(formatCalendarDate('2026-10-15', TODAY)).toBe('Thu 15 Oct')
    expect(formatCalendarDate('2027-01-04', TODAY)).toBe('4 Jan 2027')
  })
})

describe('task labels', () => {
  it('formats the day heading', () => {
    expect(formatDayHeading(TODAY)).toEqual({ weekday: 'Wednesday', date: '21 October' })
  })

  it('hides the time of a date-only Start on and shows it otherwise, in the app zone', () => {
    expect(formatSchedule('2026-10-21T21:00:00Z', JERUSALEM, TODAY)).toBe('Tomorrow')
    expect(formatSchedule('2026-10-21T13:00:00Z', JERUSALEM, TODAY)).toBe('Today · 16:00')
  })

  it('reads deadlines and completion naturally', () => {
    expect(formatDeadline('2026-10-21', TODAY)).toBe('Due today')
    expect(formatDeadline('2026-10-23', TODAY)).toBe('Due Fri')
    expect(formatCompleted('2026-10-20T09:00:00Z', JERUSALEM, TODAY)).toBe('Done yesterday')
  })

  it('formats durations compactly', () => {
    expect(formatDuration(45)).toBe('45m')
    expect(formatDuration(60)).toBe('1h')
    expect(formatDuration(90)).toBe('1h 30m')
  })
})

describe('inSentence', () => {
  it('lowercases only leading relative words', () => {
    expect(inSentence('Tomorrow')).toBe('tomorrow')
    expect(inSentence('Today · 16:30')).toBe('today · 16:30')
    expect(inSentence('Mon 26 Oct')).toBe('Mon 26 Oct')
  })
})

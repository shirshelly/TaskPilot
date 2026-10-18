import { describe, expect, it } from 'vitest'
import { addDays, appToday, daysBetween, isDateOnly, startOfDayIso, zonedDateTimeIso, zonedParts } from './time'

const JERUSALEM = 'Asia/Jerusalem'
const SANTIAGO = 'America/Santiago'
const instantOf = (iso: string) => new Date(iso).toISOString()

describe('appToday', () => {
  it('uses the application zone, not UTC', () => {
    // 22:30Z on 20 Oct is already 01:30 on 21 Oct in Jerusalem (UTC+3).
    const now = Date.parse('2026-10-20T22:30:00Z')
    expect(appToday(JERUSALEM, now)).toBe('2026-10-21')
    expect(appToday('UTC', now)).toBe('2026-10-20')
  })

  it('changes exactly at local midnight', () => {
    expect(appToday(JERUSALEM, Date.parse('2026-10-20T20:59:59Z'))).toBe('2026-10-20')
    expect(appToday(JERUSALEM, Date.parse('2026-10-20T21:00:00Z'))).toBe('2026-10-21')
  })
})

describe('startOfDayIso (date-only Start on)', () => {
  it('is local midnight with the offset in effect that day, across the autumn DST change', () => {
    // Israel leaves DST at 02:00 on Sun 25 Oct 2026: midnight that day is still UTC+3.
    expect(startOfDayIso('2026-10-25', JERUSALEM)).toBe('2026-10-25T00:00:00.000+03:00')
    expect(startOfDayIso('2026-10-26', JERUSALEM)).toBe('2026-10-26T00:00:00.000+02:00')
    expect(instantOf(startOfDayIso('2026-10-26', JERUSALEM))).toBe('2026-10-25T22:00:00.000Z')
  })

  it('is the first existing instant when DST skips midnight', () => {
    // Chile springs forward at 24:00 on 5 Sep 2026: 6 Sep starts at 01:00 (UTC-3).
    expect(startOfDayIso('2026-09-06', SANTIAGO)).toBe('2026-09-06T01:00:00.000-03:00')
  })
})

describe('zonedDateTimeIso (Start on with a time)', () => {
  it('uses the correct offset on each side of a DST change', () => {
    expect(zonedDateTimeIso('2026-10-24', '09:00', JERUSALEM)).toBe('2026-10-24T09:00:00.000+03:00')
    expect(zonedDateTimeIso('2026-10-25', '09:00', JERUSALEM)).toBe('2026-10-25T09:00:00.000+02:00')
  })

  it('moves a time skipped by spring-forward ahead', () => {
    // Israel enters DST at 02:00 on Fri 26 Mar 2027; 02:30 does not exist.
    expect(zonedDateTimeIso('2027-03-26', '02:30', JERUSALEM)).toBe('2027-03-26T03:30:00.000+03:00')
  })

  it('resolves a repeated autumn time to its earlier occurrence', () => {
    expect(zonedDateTimeIso('2026-10-25', '01:30', JERUSALEM)).toBe('2026-10-25T01:30:00.000+03:00')
  })
})

describe('zonedParts', () => {
  it('reads an instant as wall-clock date and time in the zone', () => {
    expect(zonedParts('2026-10-21T13:05:00Z', JERUSALEM)).toEqual({ date: '2026-10-21', time: '16:05' })
  })
})

describe('isDateOnly', () => {
  it('recognises the start of day even when the backend returns it normalised to UTC', () => {
    expect(isDateOnly('2026-10-20T21:00:00Z', JERUSALEM)).toBe(true)
    expect(isDateOnly('2026-10-21T00:00:00+03:00', JERUSALEM)).toBe(true)
  })

  it('is false for any other time of day', () => {
    expect(isDateOnly('2026-10-21T06:00:00Z', JERUSALEM)).toBe(false)
    expect(isDateOnly('2026-10-20T21:01:00Z', JERUSALEM)).toBe(false)
  })

  it('treats the 01:00 start of a day without a midnight as date-only', () => {
    expect(isDateOnly('2026-09-06T04:00:00Z', SANTIAGO)).toBe(true)
  })
})

describe('calendar arithmetic', () => {
  it('adds days across month and year ends', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
  })

  it('counts whole days regardless of DST', () => {
    expect(daysBetween('2026-10-24', '2026-10-26')).toBe(2)
    expect(daysBetween('2026-10-26', '2026-10-24')).toBe(-2)
  })
})

import { describe, expect, it } from 'vitest'
import { isPreset, minutesPhrase, parseAvailableMinutes, presetLabel } from './minutes'

describe('parseAvailableMinutes', () => {
  it('accepts positive whole numbers', () => {
    expect(parseAvailableMinutes('30')).toBe(30)
    expect(parseAvailableMinutes('45')).toBe(45)
    expect(parseAvailableMinutes(' 90 ')).toBe(90)
  })

  it.each([null, undefined, '', '0', '-5', 'abc', '1.5', '1e3', '30m', '9999999'])(
    'treats %j as "Any time"',
    (raw) => expect(parseAvailableMinutes(raw)).toBeNull(),
  )
})

describe('labels', () => {
  it('recognises presets', () => {
    expect(isPreset(30)).toBe(true)
    expect(isPreset(45)).toBe(false)
    expect(isPreset(null)).toBe(false)
  })

  it('formats chips and sentences', () => {
    expect([15, 60, 120, 90].map(presetLabel)).toEqual(['15m', '1h', '2h', '90m'])
    expect([30, 60, 120, 1].map(minutesPhrase)).toEqual(['30 minutes', '1 hour', '2 hours', '1 minute'])
  })
})

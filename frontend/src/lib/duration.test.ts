import { describe, expect, it } from 'vitest'
import { durationDraftFromMinutes, parseDuration, switchUnit, type DurationDraft } from './duration'

const h = (text: string): DurationDraft => ({ text, unit: 'h' })
const min = (text: string): DurationDraft => ({ text, unit: 'min' })

describe('parseDuration', () => {
  it('reads whole minutes', () => {
    expect(parseDuration(min('45'))).toEqual({ ok: true, minutes: 45 })
  })

  it('converts decimal hours exactly', () => {
    expect(parseDuration(h('1.5'))).toEqual({ ok: true, minutes: 90 })
    expect(parseDuration(h('4.5'))).toEqual({ ok: true, minutes: 270 })
    expect(parseDuration(h('1.25'))).toEqual({ ok: true, minutes: 75 })
    expect(parseDuration(h('0.1'))).toEqual({ ok: true, minutes: 6 })
    expect(parseDuration(h('2,5'))).toEqual({ ok: true, minutes: 150 })
    expect(parseDuration(h('10'))).toEqual({ ok: true, minutes: 600 })
  })

  it('treats empty as "no value"', () => {
    expect(parseDuration(min(''))).toEqual({ ok: true, minutes: null })
    expect(parseDuration(h('  '))).toEqual({ ok: true, minutes: null })
  })

  it.each([
    ['fractional minutes', min('1.5')],
    ['hours that are not whole minutes', h('1.33')],
    ['hours that are not whole minutes (tiny)', h('0.001')],
    ['zero', min('0')],
    ['zero hours', h('0.0')],
    ['negative', min('-5')],
    ['text', h('abc')],
    ['too many decimals', h('1.00001')],
    ['too long', min('1000000')],
  ])('rejects %s without rounding', (_label, draft) => {
    expect(parseDuration(draft).ok).toBe(false)
  })
})

describe('switchUnit', () => {
  it('keeps the underlying duration both ways', () => {
    const toHours = switchUnit(min('90'), 'h')
    expect(toHours).toEqual({ ok: true, draft: h('1.5') })
    expect(switchUnit(h('1.5'), 'min')).toEqual({ ok: true, draft: min('90') })
    expect(switchUnit(min('75'), 'h')).toEqual({ ok: true, draft: h('1.25') })
  })

  it('survives repeated switching without drift', () => {
    let draft = min('270')
    for (let i = 0; i < 10; i++) {
      const hours = switchUnit(draft, 'h')
      if (!hours.ok) throw new Error('expected exact')
      const back = switchUnit(hours.draft, 'min')
      if (!back.ok) throw new Error('expected exact')
      draft = back.draft
    }
    expect(draft).toEqual(min('270'))
  })

  it('refuses hours it cannot write exactly, instead of rounding', () => {
    expect(switchUnit(min('100'), 'h')).toEqual({ ok: false, reason: "100 min can't be written exactly in hours." })
  })

  it('reinterprets empty or unfinished input in the new unit', () => {
    expect(switchUnit(min(''), 'h')).toEqual({ ok: true, draft: h('') })
    expect(switchUnit(min('1.5'), 'h')).toEqual({ ok: true, draft: h('1.5') })
  })
})

describe('durationDraftFromMinutes', () => {
  it('opens round values in hours and the rest in minutes', () => {
    expect(durationDraftFromMinutes(270)).toEqual(h('4.5'))
    expect(durationDraftFromMinutes(600)).toEqual(h('10'))
    expect(durationDraftFromMinutes(75)).toEqual(h('1.25'))
    expect(durationDraftFromMinutes(45)).toEqual(min('45'))
    expect(durationDraftFromMinutes(100)).toEqual(min('100'))
    expect(durationDraftFromMinutes(null)).toEqual(min(''))
  })
})

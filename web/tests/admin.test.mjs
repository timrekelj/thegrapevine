import assert from 'node:assert/strict'
import test from 'node:test'
import { fillDays, formatTokens, formatUsd, num, parseWindow } from '../lib/admin.ts'
import { isUuid } from '../lib/validate.ts'

test('money keeps sub-cent precision so single model calls can be compared', () => {
  assert.equal(formatUsd(0), '$0.00')
  assert.equal(formatUsd('12.3456'), '$12.35')
  assert.equal(formatUsd(0.0042), '$0.0042')
  assert.equal(formatUsd(0.0000933), '$0.000093')
  assert.equal(formatUsd(null), '$0.00')
})

test('token counts shorten only when large', () => {
  assert.equal(formatTokens(950), '950')
  assert.equal(formatTokens(9_999), '9,999')
  assert.equal(formatTokens(48_200), '48k')
  assert.equal(formatTokens(2_450_000), '2.5M')
})

test('daily spend is zero-filled oldest first, including today', () => {
  const now = new Date('2026-10-22T09:00:00Z')
  const days = fillDays([{ day: '2026-10-20', cost: '0.5', tokens: 100, requests: 2 }], 3, now)
  assert.deepEqual(days.map(d => d.day), ['2026-10-20', '2026-10-21', '2026-10-22'])
  assert.equal(days[0].cost, 0.5)
  assert.equal(days[1].cost, 0)
})

test('query parameters are validated', () => {
  assert.equal(parseWindow('7'), 7)
  assert.equal(parseWindow('365'), 30)
  assert.equal(parseWindow(undefined), 30)
  assert.ok(isUuid('00000000-0000-0000-0000-0000000000a1'))
  assert.ok(!isUuid("'; drop table"))
  assert.equal(num('abc'), 0)
})

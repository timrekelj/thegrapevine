import assert from 'node:assert/strict'
import test from 'node:test'
import { cutEdition, EDITION_MAX, EDITION_MIN } from '../lib/queries.ts'

const now = Date.parse('2026-10-23T12:00:00Z')
const daysAgo = days => new Date(now - days * 86_400_000).toISOString()
const stories = ages => ages.map((days, i) => ({ id: `s${i}`, last_activity_at: daysAgo(days) }))

test('the edition keeps this week and points the archive at the last kept story', () => {
  const { stories: kept, earlier } = cutEdition(stories([0, 1, 2, 3, 4, 5, 6, 6.5, 7.5, 8, 9, 20]), now)
  assert.equal(kept.length, EDITION_MIN, 'a week of 8 stories is topped up to the minimum with older ones')
  assert.equal(earlier, kept.at(-1).last_activity_at)
})

test('a busy week keeps every recent story', () => {
  const { stories: kept, earlier } = cutEdition(stories([...Array(15).fill(1), 9, 10]), now)
  assert.equal(kept.length, 15)
  assert.equal(earlier, daysAgo(1))
})

test('a quiet paper keeps its last stories however old', () => {
  const { stories: kept, earlier } = cutEdition(stories([30, 40, 50]), now)
  assert.equal(kept.length, 3)
  assert.equal(earlier, null, 'nothing older exists')
})

test('the edition is capped, and the extra row means the archive has more', () => {
  const { stories: kept, earlier } = cutEdition(stories(Array(EDITION_MAX + 1).fill(0)), now)
  assert.equal(kept.length, EDITION_MAX)
  assert.equal(earlier, kept.at(-1).last_activity_at)
  assert.deepEqual(cutEdition([], now), { stories: [], earlier: null })
})

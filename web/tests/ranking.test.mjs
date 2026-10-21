import assert from 'node:assert/strict'
import test from 'node:test'
import { rankStories } from '../lib/queries.ts'

const now = Date.parse('2026-10-12T12:00:00Z')
const ago = hours => new Date(now - hours * 3_600_000).toISOString()
const story = (id, hours, messages = [], sources = []) => ({
  id, title: id, summary: '', category: null, created_at: ago(100),
  last_activity_at: ago(hours), messages: messages.map(h => ({ created_at: ago(h) })), sources: sources.map(h => ({ created_at: ago(h) })),
})

test('ranking keeps the latest activity in the lead and orders the rest by weighted 48h activity', () => {
  const quietLead = story('lead', 0)
  const busy = story('busy', 4, [1, 2, 3])
  const sourced = story('source', 4, [], [1, 2])
  const recent = story('recent', 1)
  const stale = story('stale', 90, [60, 70, 80], [60])
  const input = [stale, sourced, busy, recent, quietLead]
  assert.deepEqual(rankStories(input, now).map(s => s.id), ['lead', 'busy', 'source', 'recent', 'stale'])
  assert.equal(input[0], stale, 'ranking must not mutate the printed order')
})

test('ranking handles empty editions, single stories and stable ties', () => {
  const quietLead = story('lead', 0)
  assert.deepEqual(rankStories([], now), [])
  assert.deepEqual(rankStories([quietLead], now), [quietLead])
  assert.deepEqual(rankStories([story('b', 0), story('a', 0)], now).map(s => s.id), ['a', 'b'])
})

test('recent activity outranks quieter stories even when both are outside the window', () => {
  const quietLead = story('lead', 0)
  assert.equal(rankStories([story('old-busy', 60, [48]), story('old-quiet', 50), quietLead], now)[1].id, 'old-busy')
})

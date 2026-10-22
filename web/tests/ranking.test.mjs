import assert from 'node:assert/strict'
import test from 'node:test'
import { printEdition, rankStories } from '../lib/queries.ts'

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

test('an edition marks only what arrived since the last visit and credits who shared it', () => {
  const members = [{ id: 'u1', display_name: 'Nika', monogram: 'N' }, { id: 'u2', display_name: 'Luka', monogram: 'L' }]
  const s = { ...story('s', 1), messages: [{ id: 'm1', created_at: ago(5) }, { id: 'm2', created_at: ago(1) }],
    sources: [{ id: 'a', publisher: '', domain: 'delo.si', image_url: null, submitted_by: 'u1', created_at: ago(6) }, { id: 'b', publisher: 'BBC', domain: 'bbc.co.uk', image_url: '/seed/x.jpg', submitted_by: 'u1', created_at: ago(2) }] }
  const [printed] = printEdition([s], ago(3), members, now)
  assert.deepEqual(printed.sources.map(x => x.id), ['b', 'a'])
  assert.deepEqual(printed.publishers, ['BBC', 'delo.si'])
  assert.equal(printed.newSources, 1)
  assert.equal(printed.newMessages, 1)
  assert.deepEqual(printed.submitters.map(m => m.id), ['u1'])
  assert.equal(printed.imageUrl, '/seed/x.jpg')
})

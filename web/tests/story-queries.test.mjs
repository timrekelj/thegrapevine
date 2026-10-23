import assert from 'node:assert/strict'
import test from 'node:test'
import { mergeTimeline, signMedia, validateAttachments } from '../components/story/queries.ts'

test('timeline interleaves sources and messages chronologically with stable tie-breaks', () => {
  const sources = [{ id: 'b', created_at: '2026-10-12T12:00:00Z' }, { id: 'a', created_at: '2026-10-12T10:00:00Z' }]
  const messages = [{ id: 'c', created_at: '2026-10-12T11:00:00Z' }, { id: 'd', created_at: '2026-10-12T12:00:00Z' }]
  assert.deepEqual(mergeTimeline(sources, messages).map(entry => entry.id), ['a', 'c', 'b', 'd'])
})

test('attachment validation caps image count, video size and file types', () => {
  assert.equal(validateAttachments(Array.from({ length: 10 }, () => ({ type: 'image/png', size: 1 }))), null)
  assert.match(validateAttachments(Array.from({ length: 11 }, () => ({ type: 'image/png', size: 1 }))), /10 images/)
  assert.equal(validateAttachments([{ type: 'video/mp4', size: 20 * 1024 * 1024 }]), null)
  assert.match(validateAttachments([{ type: 'video/mp4', size: 20 * 1024 * 1024 + 1 }]), /20 MB/)
  assert.match(validateAttachments([{ type: 'application/pdf', size: 1 }]), /image or video/)
})

test('private attachments are signed in one request and reused until close to expiry', async () => {
  const calls = []
  const client = { storage: { from: () => ({ createSignedUrls: async paths => { calls.push(paths); return { data: paths.map(path => ({ path, signedUrl: `https://cdn/${path}?token=${calls.length}` })), error: null } } }) } }
  const now = 1_000_000
  const first = [{ id: '1', url: 'g/u/a.jpg' }, { id: '2', url: 'g/u/b.jpg' }, { id: '3', url: 'https://media.giphy.com/x.gif' }, { id: '4', url: 'g/u/a.jpg' }]
  await signMedia(client, first, [], now)
  assert.deepEqual(calls, [['g/u/a.jpg', 'g/u/b.jpg']])
  assert.equal(first[2].display_url, 'https://media.giphy.com/x.gif')
  assert.equal(first[3].display_url, first[0].display_url)
  const again = [{ id: '1', url: 'g/u/a.jpg' }, { id: '5', url: 'g/u/c.jpg' }]
  await signMedia(client, again, first, now + 60_000)
  assert.equal(again[0].display_url, first[0].display_url, 'a refresh must not change a still-valid URL')
  assert.deepEqual(calls[1], ['g/u/c.jpg'])
  const later = [{ id: '1', url: 'g/u/a.jpg' }]
  await signMedia(client, later, first, now + 3_600_000)
  assert.notEqual(later[0].display_url, first[0].display_url, 'an expiring URL is signed afresh')
})

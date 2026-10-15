import assert from 'node:assert/strict'
import { mergeTimeline, validateAttachments } from './queries.ts'

const sources = [{ id: 'b', created_at: '2026-10-12T12:00:00Z' }, { id: 'a', created_at: '2026-10-12T10:00:00Z' }]
const messages = [{ id: 'c', created_at: '2026-10-12T11:00:00Z' }, { id: 'd', created_at: '2026-10-12T12:00:00Z' }]
assert.deepEqual(mergeTimeline(sources, messages).map(entry => entry.id), ['a', 'c', 'b', 'd'])
assert.equal(validateAttachments(Array.from({ length: 10 }, () => ({ type: 'image/png', size: 1 }))), null)
assert.match(validateAttachments(Array.from({ length: 11 }, () => ({ type: 'image/png', size: 1 }))), /10 images/)
assert.equal(validateAttachments([{ type: 'video/mp4', size: 20 * 1024 * 1024 }]), null)
assert.match(validateAttachments([{ type: 'video/mp4', size: 20 * 1024 * 1024 + 1 }]), /20 MB/)
assert.match(validateAttachments([{ type: 'application/pdf', size: 1 }]), /image or video/)
console.log('PASS: timeline interleaving/ties, image count, video boundary, file types')

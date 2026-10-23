import assert from 'node:assert/strict'
import test from 'node:test'
import { safeUrl } from '../lib/validate.ts'
import { clock, dayline, plural } from '../lib/format.ts'

test('only web links and same-site paths become an href or src', () => {
  for (const url of ['https://delo.si/a', 'HTTP://example.com', '/seed/storm.jpg']) assert.equal(safeUrl(url), url)
  for (const url of [null, undefined, '', 'javascript:alert(1)', 'data:text/html,x', '//evil.example/x', '/\\evil.example', 'ftp://x']) {
    assert.equal(safeUrl(url), undefined)
  }
})

test('dates and times print in Ljubljana time wherever they are rendered', () => {
  assert.equal(clock('2026-10-22T21:05:00Z'), '23:05')
  assert.equal(dayline('2026-10-22T23:30:00Z'), 'Friday 23 October')
  assert.equal(dayline('2026-10-22T12:00:00Z', true), 'Thursday, 22 October 2026')
  assert.equal(plural(1, 'story', 'stories'), '1 story')
  assert.equal(plural(3, 'reader'), '3 readers')
})

import assert from 'node:assert/strict'
import test from 'node:test'
import { isEmail, safeUrl } from '../lib/validate.ts'
import { emailChangeNotice, sameEmail } from '../lib/account.ts'
import { clock, dayline, plural } from '../lib/format.ts'

test('only web links and same-site paths become an href or src', () => {
  for (const url of ['https://delo.si/a', 'HTTP://example.com', '/seed/storm.jpg']) assert.equal(safeUrl(url), url)
  for (const url of [null, undefined, '', 'javascript:alert(1)', 'data:text/html,x', '//evil.example/x', '/\\evil.example', 'ftp://x']) {
    assert.equal(safeUrl(url), undefined)
  }
})

test('email addresses are ordinary addresses', () => {
  assert.equal(isEmail('tim@example.com'), true)
  assert.equal(isEmail('a@b.c'), true)
  for (const value of ['', 'not-an-email', 'a@b', 'a b@c.com', `${'a'.repeat(250)}@example.com`]) assert.equal(isEmail(value), false)
})

test('email change copy explains both confirmations and a finished change', () => {
  const pending = emailChangeNotice('', 'old@example.com', 'new@example.com')
  assert.match(pending, /old@example.com/)
  assert.match(pending, /new@example.com/)
  assert.match(pending, /both/)
  const halfway = emailChangeNotice('email', 'old@example.com', 'new@example.com')
  assert.match(halfway, /One confirmation arrived/)
  assert.match(halfway, /until both are confirmed/)
  assert.match(emailChangeNotice('email', 'new@example.com', null), /Your email is confirmed/)
  assert.equal(emailChangeNotice('', 'new@example.com', null), '')
  assert.match(emailChangeNotice('sent', null, null), /confirmation link/)
})

test('deletion confirmation matches the account email, ignoring case and surrounding space', () => {
  assert.equal(sameEmail(' Tim@Example.com ', 'tim@example.com'), true)
  assert.equal(sameEmail('tim@example.com', 'other@example.com'), false)
  assert.equal(sameEmail('', 'tim@example.com'), false)
  assert.equal(sameEmail(undefined, ''), false)
})

test('dates and times print in Ljubljana time wherever they are rendered', () => {
  assert.equal(clock('2026-10-22T21:05:00Z'), '23:05')
  assert.equal(dayline('2026-10-22T23:30:00Z'), 'Friday 23 October')
  assert.equal(dayline('2026-10-22T12:00:00Z', true), 'Thursday, 22 October 2026')
  assert.equal(plural(1, 'story', 'stories'), '1 story')
  assert.equal(plural(3, 'reader'), '3 readers')
})

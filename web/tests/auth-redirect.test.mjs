import assert from 'node:assert/strict'
import test from 'node:test'
import { safeRedirect } from '../lib/auth-redirect.ts'

test('sign-in preserves invite and story destinations', () => {
  assert.equal(safeRedirect('/invite?code=grapevinedemo'), '/invite?code=grapevinedemo')
  assert.equal(safeRedirect('/story/123#discussion'), '/story/123#discussion')
  assert.equal(safeRedirect('/settings?notice=email'), '/settings?notice=email')
})

test('sign-in rejects external and malformed redirects', () => {
  for (const value of [undefined, null, [], 'https://evil.example', '//evil.example', '/\\evil.example', '/\nevil.example']) {
    assert.equal(safeRedirect(value), '/')
  }
})

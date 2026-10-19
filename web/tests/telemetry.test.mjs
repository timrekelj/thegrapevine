import assert from 'node:assert/strict'
import test from 'node:test'
import { sanitizeProperties } from '../lib/telemetry-config.ts'

test('telemetry strips emails and invite codes from URLs and redacts exception text', () => {
  const input = {
    $current_url: 'https://thegrapevine.si/login?email=private@example.com#code',
    $set_once: { $initial_referrer: 'https://thegrapevine.si/invite?code=secret' },
    $exception_message: 'private text',
    $exception_list: [{ type: 'TypeError', value: 'private message', stacktrace: { frames: [{ function: 'send' }] } }],
  }
  const result = sanitizeProperties(input)
  assert.equal(result.$current_url, 'https://thegrapevine.si/login')
  assert.equal(result.$set_once.$initial_referrer, 'https://thegrapevine.si/invite')
  assert.equal(result.$exception_message, undefined)
  assert.equal(result.$exception_list[0].value, 'Redacted')
  assert.equal(result.$exception_list[0].stacktrace.frames[0].function, 'send')
  assert.equal(input.$exception_list[0].value, 'private message', 'the original event must not be mutated')
})

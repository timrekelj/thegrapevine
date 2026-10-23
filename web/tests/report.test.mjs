import assert from 'node:assert/strict'
import test from 'node:test'
import { describeBrowser, reportHref, reportKind, screenshotProblem } from '../lib/report.ts'

test('report links pre-fill a bug with the error and a same-site origin only', () => {
  assert.equal(reportHref('Could not post.', '/story/abc'), '/report?kind=bug&error=Could+not+post.&from=%2Fstory%2Fabc')
  assert.equal(reportHref(undefined, 'https://evil.example'), '/report?kind=bug')
  assert.equal(reportHref(undefined, '/report?kind=bug'), '/report?kind=bug')
  assert.equal(new URLSearchParams(reportHref('x'.repeat(900)).split('?')[1]).get('error').length, 500)
})

test('unknown kinds fall back to a bug', () => {
  assert.equal(reportKind('feature'), 'feature')
  assert.equal(reportKind('question'), 'bug')
  assert.equal(reportKind(undefined), 'bug')
})

test('screenshots are images GitHub renders, at most 10 MB', () => {
  assert.equal(screenshotProblem({ type: 'image/png', size: 1024 }), null)
  assert.match(screenshotProblem({ type: 'image/heic', size: 1024 }), /JPEG/)
  assert.match(screenshotProblem({ type: 'image/svg+xml', size: 1024 }), /JPEG/)
  assert.match(screenshotProblem({ type: 'image/jpeg', size: 11 * 1024 * 1024 }), /10 MB/)
})

test('browsers are described without the full user agent', () => {
  assert.deepEqual(describeBrowser('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/19.0 Safari/605.1.15'), { browser: 'Safari 19.0', os: 'macOS' })
  assert.deepEqual(describeBrowser('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0'), { browser: 'Edge 142', os: 'Windows' })
  assert.deepEqual(describeBrowser('Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36'), { browser: 'Chrome 142', os: 'Android' })
  assert.deepEqual(describeBrowser('curl/8'), { browser: 'Unknown browser', os: 'Unknown OS' })
})

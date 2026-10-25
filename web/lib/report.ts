// In-app reports (issue #35): the form at /report files a GitHub issue through submit-feedback.

export const REPORT_KINDS = [
  { value: 'bug', label: 'Bug', hint: 'What happened, and what did you expect instead?' },
  { value: 'enhancement', label: 'Improvement', hint: 'What could work better, and how?' },
  { value: 'feature', label: 'New feature', hint: 'What would you like the paper to do?' },
] as const
export type ReportKind = typeof REPORT_KINDS[number]['value']

export const MAX_SCREENSHOTS = 5
export const MAX_SCREENSHOT_BYTES = 10 * 1024 * 1024
// The formats the feedback bucket accepts and GitHub renders inline.
export const SCREENSHOT_TYPES: Record<string, string> = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/gif': 'gif', 'image/webp': 'webp' }

export const reportKind = (value: unknown): ReportKind =>
  REPORT_KINDS.some(kind => kind.value === value) ? value as ReportKind : 'bug'

/** A link to the report form, pre-filled as a bug with the error a member just saw. */
export function reportHref(error?: string, from?: string) {
  const params = new URLSearchParams({ kind: 'bug' })
  if (error) params.set('error', error.slice(0, 500))
  if (from?.startsWith('/') && !from.startsWith('/report')) params.set('from', from.slice(0, 200))
  return `/report?${params}`
}

/** Why a file can't be attached, or `null` if it can. */
export function screenshotProblem(file: { type: string; size: number }) {
  if (!SCREENSHOT_TYPES[file.type]) return 'Screenshots must be JPEG, PNG, GIF or WebP images.'
  if (file.size > MAX_SCREENSHOT_BYTES) return 'Each screenshot must be 10 MB or smaller.'
  return null
}

/** "Safari 19 on macOS" from a user agent: enough to reproduce a bug, without the full string. */
export function describeBrowser(ua: string) {
  const browser = [
    ['Edge', /Edg\/(\d+)/], ['Opera', /OPR\/(\d+)/], ['Firefox', /Firefox\/(\d+)/],
    ['Chrome', /Chrome\/(\d+)/], ['Safari', /Version\/(\d+(?:\.\d+)?).*Safari/],
  ].map(([name, pattern]) => { const match = ua.match(pattern as RegExp); return match && `${name} ${match[1]}` }).find(Boolean)
  const os = /iPhone|iPad|iPod/.test(ua) ? 'iOS' : /Android/.test(ua) ? 'Android' : /Mac OS X/.test(ua) ? 'macOS'
    : /Windows/.test(ua) ? 'Windows' : /CrOS/.test(ua) ? 'ChromeOS' : /Linux/.test(ua) ? 'Linux' : null
  return { browser: browser || 'Unknown browser', os: os || 'Unknown OS' }
}

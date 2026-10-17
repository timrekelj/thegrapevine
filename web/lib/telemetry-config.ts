// Public ingestion token, not a personal API key.
export const POSTHOG_TOKEN = 'phc_kXSTZhnAXUB44jnt3FK8k88YnoLtXuVVZsumUBptu6j9'
export const POSTHOG_HOST = 'https://eu.i.posthog.com'
export const telemetryEnabled = process.env.NODE_ENV === 'production' &&
  (!process.env.NEXT_PUBLIC_VERCEL_ENV || process.env.NEXT_PUBLIC_VERCEL_ENV === 'production')

// Login URLs contain email addresses and invite URLs contain bearer-like codes.
export function sanitizeProperties(properties: Record<string, unknown>) {
  const clean = { ...properties }
  for (const key of ['$current_url', '$referrer', '$initial_current_url', '$initial_referrer']) {
    if (typeof clean[key] === 'string') {
      try { const url = new URL(clean[key]); clean[key] = url.origin + url.pathname } catch { delete clean[key] }
    }
  }
  for (const key of ['$set', '$set_once']) {
    if (clean[key] && typeof clean[key] === 'object') clean[key] = sanitizeProperties(clean[key] as Record<string, unknown>)
  }
  delete clean.$exception_message
  if (Array.isArray(clean.$exception_list)) {
    clean.$exception_list = clean.$exception_list.map(exception => ({ ...exception, value: 'Redacted' }))
  }
  return clean
}

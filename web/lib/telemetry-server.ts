import { PostHog } from 'posthog-node'
import { POSTHOG_HOST, POSTHOG_TOKEN, sanitizeProperties, telemetryEnabled } from './telemetry-config'

function client() {
  return new PostHog(POSTHOG_TOKEN, {
    host: POSTHOG_HOST, flushAt: 1, flushInterval: 0, requestTimeout: 2000, fetchRetryCount: 0,
    before_send: event => ({ ...event, properties: sanitizeProperties(event.properties ?? {}) }),
  })
}

export async function captureServer(userId: string, event: string, properties: Record<string, unknown> = {}) {
  if (!telemetryEnabled) return
  const posthog = client()
  try {
    posthog.capture({ distinctId: userId.toLowerCase(), event, properties: { ...properties, platform: 'web', environment: 'production' } })
    await posthog.shutdown()
  } catch { console.warn('PostHog event delivery unavailable') }
}

export async function captureServerError(error: unknown, route: string) {
  if (!telemetryEnabled) return
  const posthog = client()
  try {
    const sanitized = new Error('Server operation failed')
    if (error instanceof Error) sanitized.stack = error.stack?.split('\n').slice(1).join('\n')
    posthog.captureException(sanitized, undefined, { route, platform: 'web-server', environment: 'production', $process_person_profile: false })
    await posthog.shutdown()
  } catch { console.warn('PostHog error delivery unavailable') }
}

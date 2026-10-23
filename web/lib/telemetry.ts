'use client'

import type { PostHog } from 'posthog-js'
import { POSTHOG_HOST, POSTHOG_TOKEN, sanitizeProperties, telemetryEnabled } from './telemetry-config'

// posthog-js is about 100 KB gzipped, so it loads beside the page rather than in every page's
// main bundle. Calls made before it arrives wait in a short queue.
let client: PostHog | null = null
const queue: ((posthog: PostHog) => void)[] = []

export function startTelemetry() {
  if (!telemetryEnabled) return
  void import('posthog-js').then(({ default: posthog }) => {
    posthog.init(POSTHOG_TOKEN, {
      api_host: POSTHOG_HOST,
      autocapture: false,
      capture_pageview: false,
      capture_pageleave: false,
      capture_exceptions: true,
      disable_session_recording: true,
      disable_surveys: true,
      capture_heatmaps: false,
      enable_recording_console_log: false,
      person_profiles: 'identified_only',
      before_send: event => {
        if (event) event.properties = { ...sanitizeProperties(event.properties), platform: 'web', environment: 'production' }
        return event
      },
    })
    client = posthog
    queue.splice(0).forEach(run => run(posthog))
  }).catch(() => { /* Blocked or offline: telemetry is optional. */ })
}

export function withPosthog(run: (posthog: PostHog) => void) {
  if (!telemetryEnabled) return
  if (client) run(client)
  else if (queue.length < 50) queue.push(run)
}

export function capture(event: string, properties: Record<string, unknown> = {}) {
  withPosthog(posthog => posthog.capture(event, properties))
}

export function correctionsEnabled() {
  return !telemetryEnabled || (client?.getFeatureFlag('story-corrections-enabled') ?? true) !== false
}

export function captureError(error: unknown, operation: string) {
  const sanitized = new Error(operation)
  if (error instanceof Error) sanitized.stack = error.stack?.split('\n').slice(1).join('\n')
  withPosthog(posthog => posthog.captureException(sanitized, { operation }))
}

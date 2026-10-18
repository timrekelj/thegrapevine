'use client'

import posthog from 'posthog-js'
import { telemetryEnabled } from './telemetry-config'

export function capture(event: string, properties: Record<string, unknown> = {}) {
  if (telemetryEnabled) posthog.capture(event, properties)
}

export function correctionsEnabled() {
  return !telemetryEnabled || (posthog.getFeatureFlag('story-corrections-enabled') ?? true) !== false
}

export function captureError(error: unknown, operation: string) {
  if (!telemetryEnabled) return
  const sanitized = new Error(operation)
  if (error instanceof Error) sanitized.stack = error.stack?.split('\n').slice(1).join('\n')
  posthog.captureException(sanitized, { operation })
}

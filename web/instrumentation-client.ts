import posthog from 'posthog-js'
import { POSTHOG_HOST, POSTHOG_TOKEN, sanitizeProperties, telemetryEnabled } from './lib/telemetry-config'

if (telemetryEnabled) {
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
}

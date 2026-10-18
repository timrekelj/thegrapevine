import type { Instrumentation } from 'next'

export const onRequestError: Instrumentation.onRequestError = async (error, _request, context) => {
  const { captureServerError } = await import('./lib/telemetry-server')
  await captureServerError(error, context.routePath)
}

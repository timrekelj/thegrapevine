'use client'

import { useEffect } from 'react'
import { captureError } from '@/lib/telemetry'

export default function GlobalError({ error, reset }: { error: Error; reset: () => void }) {
  useEffect(() => { captureError(error, 'render_failed') }, [error])
  return <html lang="en"><body><h1>Something went wrong.</h1><button onClick={reset}>Try again</button></body></html>
}

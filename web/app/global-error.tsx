'use client'

import { useEffect } from 'react'
import { captureError } from '@/lib/telemetry'
import './globals.css'

export default function GlobalError({ error, reset }: { error: Error; reset: () => void }) {
  useEffect(() => { captureError(error, 'render_failed') }, [error])
  return <html lang="en"><body>
    <main className="fatal">
      <p className="masthead">The Grapevine</p>
      <p className="label">Stop the presses</p>
      <h1 className="display">Something went wrong.</h1>
      <button className="btn" onClick={reset}>Try again</button>
    </main>
  </body></html>
}

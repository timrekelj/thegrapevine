'use client'

import { useEffect } from 'react'
import { captureError } from '@/lib/telemetry'
import { reportHref } from '@/lib/report'
import './globals.css'

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => { captureError(error, 'render_failed') }, [error])
  return <html lang="en"><body>
    <main className="fatal">
      <p className="masthead">The Grapevine</p>
      <p className="label">Stop the presses</p>
      <h1 className="display">Something went wrong.</h1>
      <button className="btn" onClick={reset}>Try again</button>
      {/* A plain link: the app's layout and router are what failed. */}
      <a className="report-link meta" href={reportHref(error.digest ? `Page failed to render (${error.digest})` : 'Page failed to render', location.pathname)}>Report this problem</a>
    </main>
  </body></html>
}

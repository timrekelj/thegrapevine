'use client'

import Link from '@/components/print/PressLink'
import { useEffect } from 'react'
import { captureError } from '@/lib/telemetry'

export default function StoryError({ error, reset }: { error: Error; reset: () => void }) {
  useEffect(() => { captureError(error, 'story_render_failed') }, [error])
  return <main className="fatal">
    <h1 className="display">This story could not be loaded.</h1>
    <p className="summary">Your paper and conversation have not been changed.</p>
    <div style={{ display: 'flex', gap: 20, alignItems: 'center' }}><button className="btn" onClick={reset}>Try again</button><Link href="/" className="meta">Back to the paper</Link></div>
  </main>
}

'use client'

import Link from 'next/link'
import { useEffect } from 'react'
import { captureError } from '@/lib/telemetry'

export default function StoryError({ error, reset }: { error: Error; reset: () => void }) {
  useEffect(() => { captureError(error, 'story_render_failed') }, [error])
  return <main style={{ maxWidth: 700, margin: '48px auto', padding: 20 }}><h1 className="headline">This story could not be loaded</h1><p>Your paper and conversation have not been changed.</p><button className="btn" onClick={reset}>Try again</button> <Link href="/">Back to the paper</Link></main>
}

'use client'

import Link from 'next/link'

export default function StoryError({ reset }: { reset: () => void }) {
  return <main style={{ maxWidth: 700, margin: '48px auto', padding: 20 }}><h1 className="headline">This story could not be loaded</h1><p>Your paper and conversation have not been changed.</p><button className="btn" onClick={reset}>Try again</button> <Link href="/">Back to the paper</Link></main>
}

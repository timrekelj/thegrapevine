'use client'

import { useSyncExternalStore, useState, type ReactNode } from 'react'

/** Remember only a version explicitly collapsed by this reader; new summaries open again. */
export default function CatchUp({ storyId, userId, version, children }: { storyId: string; userId: string; version: string; children: ReactNode }) {
  const [override, setOverride] = useState<{ version: string; open: boolean } | null>(null)
  const key = `grapevine.catch-up.${userId}.${storyId}`
  const collapsedVersion = useSyncExternalStore(
    callback => { window.addEventListener('storage', callback); return () => window.removeEventListener('storage', callback) },
    () => { try { return localStorage.getItem(key) } catch { return null } },
    () => null,
  )
  const open = override?.version === version ? override.open : collapsedVersion !== version
  return <section className="story-catch-up" aria-label="Story catch-up">
    <button className="story-catch-toggle" aria-expanded={open} onClick={() => {
      setOverride({ version, open: !open })
      try { if (open) localStorage.setItem(key, version); else localStorage.removeItem(key) } catch { /* The control still works without persistence. */ }
    }}><span>The catch-up</span><span aria-hidden="true">{open ? '⌃' : '⌄'}</span></button>
    {open ? children : <p className="story-catch-preview">{version || 'Summary unavailable'}</p>}
  </section>
}

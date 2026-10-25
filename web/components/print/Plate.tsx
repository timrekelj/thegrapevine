'use client'

import { useState } from 'react'
import { safeUrl } from '@/lib/validate'

/**
 * A source's own picture, shown clean and cropped to its frame (see `.plate` in globals.css).
 * Without a usable picture it falls back to a plain tile with the publisher's name.
 */
export default function Plate({ src, label, className = '', alt = '', eager = false }: {
  src?: string | null; label: string; className?: string; alt?: string; eager?: boolean
}) {
  const [failed, setFailed] = useState(false)
  const url = safeUrl(src)
  if (!url || failed) return <div className={`typeplate ${className}`}><span>{label}</span></div>
  return <div className={`plate ${className}`}>
    {/* Publisher preview domains are arbitrary; the frame handles sizing. No referrer: publishers
        don't learn which paper a reader came from (and some hotlink checks let it through). */}
    {/* eslint-disable-next-line @next/next/no-img-element */}
    <img src={url} alt={alt} referrerPolicy="no-referrer" loading={eager ? 'eager' : 'lazy'} decoding="async" onError={() => setFailed(true)} />
  </div>
}

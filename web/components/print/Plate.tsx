'use client'

import { useState, type CSSProperties } from 'react'
import { safeUrl } from '@/lib/validate'

/**
 * A photograph printed in colour through a halftone screen (see `.plate` in globals.css).
 * Without a usable picture it falls back to a type plate with the publisher's name.
 * `screen={false}` prints the photo clean, for GIFs, whose frames could not stay in step across two copies.
 */
export default function Plate({ src, label, className = '', alt = '', screen = true, inkIn = false, eager = false, style }: {
  src?: string | null; label: string; className?: string; alt?: string; screen?: boolean; inkIn?: boolean; eager?: boolean; style?: CSSProperties
}) {
  const [failed, setFailed] = useState(false)
  const url = safeUrl(src)
  if (!url || failed) return <div className={`typeplate ${className}`} style={style}><span>{label}</span></div>
  return <div className={`plate ${inkIn ? 'ink-in' : ''} ${className}`} style={style}>
    {/* Publisher preview domains are arbitrary; the plate handles sizing. */}
    {/* eslint-disable-next-line @next/next/no-img-element */}
    <img className="photo" src={url} alt={alt} loading={eager ? 'eager' : 'lazy'} decoding="async" onError={() => setFailed(true)} />
    {screen && <div className="screen" aria-hidden="true">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={url} alt="" loading={eager ? 'eager' : 'lazy'} decoding="async" />
    </div>}
  </div>
}

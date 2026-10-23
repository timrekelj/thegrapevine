'use client'

import { useLinkStatus } from 'next/link'

/** Always rendered and sizeless: globals.css styles the link and the page around it while it is pending. */
export default function LinkHint() {
  const { pending } = useLinkStatus()
  return <span className="press-hint" data-pending={pending || undefined} aria-hidden="true" />
}

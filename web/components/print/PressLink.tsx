import Link from 'next/link'
import type { ComponentProps } from 'react'
import LinkHint from './LinkHint'

/** `next/link`, plus a hint that keeps the link pressed and runs the press bar while its page is set. */
export default function PressLink({ children, ...link }: ComponentProps<typeof Link>) {
  return <Link {...link}>{children}<LinkHint /></Link>
}

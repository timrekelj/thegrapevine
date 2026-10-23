'use client'

import { usePathname } from 'next/navigation'
import Link from '@/components/print/PressLink'
import { reportHref } from '@/lib/report'

/** "Report this" beside an error: opens the report form as a bug, with the error filled in. */
export default function ReportLink({ error, className = 'report-link' }: { error: string; className?: string }) {
  const pathname = usePathname()
  return <Link href={reportHref(error, pathname)} className={className}>Report this</Link>
}

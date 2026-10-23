'use client'

import { useFormStatus } from 'react-dom'

/** Disabled while its form’s server action is running, so a second submit cannot race the first. */
export function PendingButton({ className, idle, pending }: { className: string; idle: string; pending: string }) {
  const status = useFormStatus()
  return <button className={className} disabled={status.pending}>{status.pending ? pending : idle}</button>
}
